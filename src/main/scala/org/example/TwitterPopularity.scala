package org.example

import akka.actor._
import akka.pattern.{ ask, pipe }
import akka.dispatch.Await
import akka.util.{Duration, Timeout}
import akka.util.duration._
import com.typesafe.config.ConfigFactory
import twitter4j.conf.ConfigurationBuilder
import twitter4j._
import scala.collection.JavaConversions._


object TwitterFeedProtcol {
  case class Start(hashTag: String)
  case object Stop
  case object Busy_?
  case object Stopped
  case class Streaming(hashTag: String)
}

class TwitterFeedConsumer(twitterStream: TwitterStream, destination: ActorRef) extends Actor {
  import TwitterFeedProtcol._
  import UserRegistryProtocol._

  val listener = new StatusListener(){
    def onStatus(status: Status)  {
      destination ! AddUser(status.getUser)
    }
    def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice) {}
    def onTrackLimitationNotice(numberOfLimitedStatuses: Int) {}
    def onException(ex: Exception) {
      ex.printStackTrace()
    }
    def onStallWarning(warning: StallWarning) {}
    def onScrubGeo(userId: Long, upToStatusId: Long) {}
  }
  twitterStream.addListener(listener)

  def stopped: Receive = {
    case Start(hashTag) =>
      println("Start")
      twitterStream.filter(new FilterQuery(0, Array.empty, Array(hashTag)))
      context.become(started(hashTag))
      sender ! Streaming(hashTag)

    case Busy_? =>
      sender ! Stopped
  }

  def started(hashTag: String): Receive = {
    case Stop =>
      println("Stop Twitter stream")
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
  case class RetrieveFriends(userId: Long)
}

class UserRegistry(twitter: Twitter, voteActor: ActorRef) extends Actor with ActorLogging with Stash {
  import UserRegistryProtocol._
  import TwitterPopularityProtocol._

  val config = context.system.settings.config
  val rateLimitCheckInterval = Duration.fromNanos(config.getNanoseconds("twitter.api.rate-limit-check"))

  var users: Set[Long] = Set.empty

  override def preStart() {
    super.preStart()
    self ! CheckLimits
  }

  def inactive: Receive = {
    case CheckLimits =>
      // check back in 5
      context.system.scheduler.scheduleOnce(rateLimitCheckInterval) {
        self ! CheckLimits
      }
      val limits = twitter.getRateLimitStatus("friends").toMap
      limits.get("/friends/list").filter(_.getRemaining > 0).foreach { s =>
        log.info("Under Twitter rate-limit for friends API: {}", s)
        unstashAll()
        context.become(active)
      }
    case AddUser(user) => addUser(user.getId)
    case RetrieveFriends(_) => stash()   // TODO: persistent stash?
  }

  def active: Receive = {
    case CheckLimits =>
    case AddUser(user) => addUser(user.getId)
    case RetrieveFriends(userId) =>
      val friends = twitter.getFriendsList(userId, -1)  // TODO: cursor?
      log.info("Retrieve friends {} - Rate-limit: {}", userId, friends.getRateLimitStatus)
      friends.toList.foreach(f => voteActor ! VoteFor(f))
      Option(friends.getRateLimitStatus).filter(_.getRemaining < 1).foreach { s =>
        log.warning("Over Twitter rate-limit for friends API: {}", s)
        context.become(inactive)
      }
  }

  def receive = inactive

  private def addUser(id: Long) {
    if (!users.contains(id)) {
      log.info("Add user {}", id)
      self ! RetrieveFriends(id)
      users = users + id
    }
  }
}

object TwitterPopularityProtocol {
  case class VoteFor(id: User)
}

class TwitterPopularity extends Actor with ActorLogging {
  import TwitterPopularityProtocol._
  def receive = {
    case VoteFor(user) =>
      log.info(">>>>>>>>>>>>>>>>>>  {} - {}" + user.getId, user.getScreenName)
  }
}

object TwitterPopularity extends App {
  import akka.util.duration._
  import TwitterFeedProtcol._

  override def main(args: Array[String]) {
    implicit val timeout = Timeout(5 seconds)

    val config = ConfigFactory.load()
    val actorSystem = ActorSystem("TwitterPopularity", config)

    val t4jConf = new ConfigurationBuilder()
      .setDebugEnabled(config.getBoolean("twitter.stream.debug"))
      .setOAuthConsumerKey(config.getString("twitter.oauth.consumerKey"))
      .setOAuthConsumerSecret(config.getString("twitter.oauth.consumerSecret"))
      .setOAuthAccessToken(config.getString("twitter.oauth.accessToken"))
      .setOAuthAccessTokenSecret(config.getString("twitter.oauth.accessTokenSecret"))
      .build()
    val twitterStream = new TwitterStreamFactory(t4jConf).getInstance()
    val twitterApi = new TwitterFactory(t4jConf).getInstance()

    val twitterPopularity = actorSystem.actorOf(Props[TwitterPopularity])
    val statusListener = actorSystem.actorOf(Props(new UserRegistry(twitterApi, twitterPopularity))
      .withDispatcher("twitter.akka.stash-dispatcher"))
    val streamConsumer = actorSystem.actorOf(Props(new TwitterFeedConsumer(twitterStream, statusListener)))

    sys.addShutdownHook({
      println()
      Await.result(streamConsumer ? Stop, 3 seconds)
      actorSystem.shutdown()
    })

    streamConsumer ! Start(config.getString("twitter.stream.track"))
  }
}