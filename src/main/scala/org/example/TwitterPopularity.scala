package org.example

import akka.actor._
import akka.pattern.{ ask, pipe }
import akka.dispatch.Await
import akka.util.{Duration, Timeout}
import com.typesafe.config.ConfigFactory
import twitter4j.conf.ConfigurationBuilder
import twitter4j._
import scala.collection.JavaConversions._
import java.util.concurrent.TimeUnit
import scala.math.max
import scala.math.min


object TwitterFeedProtcol {
  case class Start(hashTag: String)
  case object Stop
  case object Busy_?
  case object Stopped
  case class Streaming(hashTag: String)
}

class TwitterFeedConsumer(twitterStream: TwitterStream, destination: ActorRef) extends Actor with ActorLogging {
  import TwitterFeedProtcol._
  import UserRegistryProtocol._

  val listener = new StatusListener(){
    // TODO: leaked internals...
    def onStatus(status: Status)  {
      destination ! AddUser(status.getUser)
      log.info("Tweet\n{}:\n{}\n\n", status.getUser.getScreenName, status.getText)
    }
    def onTrackLimitationNotice(numberOfLimitedStatuses: Int) {
      log.error("Track limitation notice of {} status", numberOfLimitedStatuses)
    }
    def onException(ex: Exception) {
      log.error("Status error ", ex)
    }
    def onStallWarning(warning: StallWarning) {
      log.warning("Stall: {}",warning)
    }
    def onScrubGeo(userId: Long, upToStatusId: Long) {}
    def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice) {}
  }
  twitterStream.addListener(listener)

  def stopped: Receive = {
    case Start(hashTag) =>
      log.info("Start")
      twitterStream.filter(new FilterQuery(0, Array.empty, Array(hashTag)))
      context.become(started(hashTag))
      sender ! Streaming(hashTag)

    case Busy_? =>
      sender ! Stopped
  }

  def started(hashTag: String): Receive = {
    case Stop =>
      log.info("Stop Twitter stream")
      twitterStream.shutdown()
      context.become(stopped)
      sender ! Stopped

    case Busy_? =>
      sender ! Streaming(hashTag)
  }

  def receive = stopped
}

object UserRegistryProtocol {
  case object CheckLimits
  case class AddUser(user: User)
  case class RetrieveFriends(userId: Long, cursor: Long)
}

class UserRegistry(twitter: Twitter, voteActor: ActorRef) extends Actor with ActorLogging with Stash {
  import UserRegistryProtocol._
  import TwitterPopularityProtocol._

  val maxLimitCheckSecs = fromConfigAsSeconds("max-rate-limit-check-interval")
  val minLimitCheckSecs = fromConfigAsSeconds("min-rate-limit-check-interval")

  var users: Set[Long] = Set.empty

  override def preStart() {
    super.preStart()
    self ! CheckLimits
  }

  def receive = inactive

  protected def inactive: Receive = {
    case CheckLimits =>
      val limit = twitter.getRateLimitStatus("friends").toMap
        .get("/friends/list")
        .getOrElse(throw new IllegalStateException("No Rate Limit"))
      log.info("Twitter rate-limit for friends API: {}, Reset in {} secs", limit.getRemaining, limit.getSecondsUntilReset)
      if (limit.getRemaining > 0) {
        unstashAll()
        log.info("become active")
        context.become(active)
      } else {
        scheduleNextLimitCheck(limit)
      }

    case AddUser(user) => addUser(user.getId)
    case RetrieveFriends(_,_) => stash()
  }

  protected def active: Receive = {
    case CheckLimits =>
    case AddUser(user) => addUser(user.getId)
    case RetrieveFriends(userId,cursor) =>
      val friends = twitter.getFriendsList(userId, cursor)
      log.info("Retrieve friends of {}. {}", userId, friends.getRateLimitStatus.getRemaining)
      friends.toList.foreach(f => voteActor ! VoteFor(f))
      Option(friends.getRateLimitStatus).filter(_.getRemaining < 1).foreach { s =>
        log.warning("Over Twitter rate-limit for friends API: {}", s)
        scheduleNextLimitCheck(s)
        context.become(inactive)
      }
      if (friends.getNextCursor > 0) self ! RetrieveFriends(userId, friends.getNextCursor)
  }

  private def fromConfigAsSeconds(property: String) = {
    (context.system.settings.config.getMilliseconds(property) / 1000L).asInstanceOf[Int]
  }

  private def scheduleNextLimitCheck(limit: RateLimitStatus){
    val next = max(min(limit.getSecondsUntilReset,maxLimitCheckSecs), minLimitCheckSecs)
    context.system.scheduler.scheduleOnce(Duration.create(next, TimeUnit.SECONDS)) {
      self ! CheckLimits
    }
  }

  private def addUser(id: Long) {
    if (!users.contains(id)) {
      log.debug("Add user {}", id)
      self ! RetrieveFriends(id, -1)
      users = users + id
    }
  }
}

object TwitterPopularityProtocol {
  case class VoteFor(id: User)
  case class Top(n: Int)
  case class TopResults(status: Seq[(User,Int)])
}

class TwitterPopularity extends Actor with ActorLogging {
  import TwitterPopularityProtocol._
  var tally: Map[User,Int] = Map.empty
  var last: String = ""

  def receive = {
    case VoteFor(user) =>
      log.debug("Vote for {} - {}", user.getId, user.getScreenName)
      tally = tally + (user -> tally.get(user).map(_+1).getOrElse(1))
    case Top(n) =>
      val topN = tally.toSeq.sortBy(_._2).takeRight(n)
      var lines = topN.reverse.map(x => x._2 + " " + x._1.getScreenName + " (" + x._1.getName + ")").mkString("\n")
      var s = "======================("+tally.size +" votes)\n"+lines
      if (last != s && tally.size > 0) println(s)
      last = s
  }
}

object TwitterPopularity extends App {
  import akka.util.duration._
  import TwitterFeedProtcol._
  import TwitterPopularityProtocol._

  override def main(args: Array[String]) {
    implicit val timeout = Timeout(5 seconds)

    val config = ConfigFactory.load()
    val actorSystem = ActorSystem("TwitterPopularity", config)

    val twitterConf = new ConfigurationBuilder()
      .setDebugEnabled(config.getBoolean("twitter.stream.debug"))
      .setOAuthConsumerKey(config.getString("twitter.oauth.consumerKey"))
      .setOAuthConsumerSecret(config.getString("twitter.oauth.consumerSecret"))
      .setOAuthAccessToken(config.getString("twitter.oauth.accessToken"))
      .setOAuthAccessTokenSecret(config.getString("twitter.oauth.accessTokenSecret"))
      .build()
    val twitterStream = new TwitterStreamFactory(twitterConf).getInstance()
    val twitterApi = new TwitterFactory(twitterConf).getInstance()

    val twitterPopularity = actorSystem.actorOf(Props[TwitterPopularity])
    val statusListener = actorSystem.actorOf(Props(new UserRegistry(twitterApi, twitterPopularity))
      .withDispatcher("twitter.akka.stash-dispatcher"))
    val streamConsumer = actorSystem.actorOf(Props(new TwitterFeedConsumer(twitterStream, statusListener)))

    sys.addShutdownHook({
      println()
      Await.result(streamConsumer ? Stop, 5 seconds)
      actorSystem.shutdown()
    })

    val track = args.headOption.getOrElse(config.getString("twitter.stream.track"))
    streamConsumer ! Start(track)

    // Print periodic results
    actorSystem.scheduler.schedule(10 seconds, 10 seconds) {
      twitterPopularity ! Top(10)
    }
  }
}