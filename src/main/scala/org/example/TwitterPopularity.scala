package org.example

import akka.actor._
import akka.pattern.{ask, gracefulStop}
import akka.dispatch.{Future, Await}
import akka.util.duration._
import akka.util.{Duration, Timeout}
import com.typesafe.config.ConfigFactory
import twitter4j.conf.ConfigurationBuilder
import twitter4j._
import java.util.concurrent.TimeUnit
import scala.math.max
import scala.math.min
import scala.collection.JavaConversions._
import org.slf4j.{LoggerFactory, Logger}
import java.io._
import resource._


object TwitterFeedProtcol {
  case class Start(hashTag: String*)
  case object Stop
  case object Busy_?
  case object Stopped
  case class Streaming(hashTag: String*)
}

class TwitterStreamConsumer(twitterStream: TwitterStream) extends Actor with ActorLogging {
  import TwitterFeedProtcol._
  import VotersProtocol._

  override def postStop() {
    super.postStop()
    twitterStream.shutdown()
  }

  def stopped: Receive = {
    case Start(hashTag) =>
      twitterStream.filter(new FilterQuery(0, Array.empty, Array(hashTag)))
      context.become(started(hashTag))
      sender ! Streaming(hashTag)

    case Busy_? =>
      sender ! Stopped
  }

  def started(hashTag: String): Receive = {
    case Stop =>
      twitterStream.cleanUp()
      context.become(stopped)
      sender ! Stopped

    case Busy_? =>
      sender ! Streaming(hashTag)
  }

  def receive = stopped
}

class TwitterStreamListener(voters: ActorRef) extends StatusListener  {
  import VotersProtocol._

  private val log: Logger = LoggerFactory.getLogger(classOf[TwitterStreamListener])

  def onStatus(status: Status)  {
    voters ! AddUser(status.getUser)
    log.info("Tweet\n" + status.getUser.getScreenName + ":\n" + status.getText + "\n\n")
  }

  def onTrackLimitationNotice(numberOfLimitedStatuses: Int) {
    log.warn("Track limitation notice of {} status", numberOfLimitedStatuses)
  }

  def onException(ex: Exception) {
    log.error("Status error ", ex)
  }

  def onStallWarning(warning: StallWarning) {
    log.warn("Stall: {}", warning)
  }

  def onScrubGeo(userId: Long, upToStatusId: Long) {}
  def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice) {}
}

object VotersProtocol {
  case object CheckLimits
  case class AddUser(user: User)
  case class RetrieveFriends(userId: Long, cursor: Long)
}

class Voters(twitter: Twitter, voteActor: ActorRef) extends Actor with ActorLogging with Stash {
  import VotersProtocol._
  import TallyProtocol._

  val maxLimitCheckSecs = fromConfigAsSeconds("twitter.api.max-rate-limit-check-interval")
  val minLimitCheckSecs = fromConfigAsSeconds("twitter.api.min-rate-limit-check-interval")

  var voters: Set[Long] = Set.empty

  val file = "/tmp/"+classOf[Voters].getCanonicalName

  override def preStart() {
    super.preStart()
    if(new File(file).exists()) {
      val result = managed(new ObjectInputStream((new FileInputStream(file)))) map {
        input =>
          input.readObject().asInstanceOf[Set[Long]]
      }
      voters = result.opt.getOrElse(Set.empty)
      log.info("wrote voters from " + file)
    }
    self ! CheckLimits
  }

  override def postStop() {
    super.postStop()
    val out = new ObjectOutputStream(new FileOutputStream(file))
    out.writeObject(voters)
    out.close()
    log.info("wrote voters to " + file)
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
    if (!voters.contains(id)) {
      log.debug("Add user {}", id)
      self ! RetrieveFriends(id, -1)
      voters = voters + id
    }
  }
}

object TallyProtocol {
  case class VoteFor(id: User)
  case class Top(n: Int)
  case class TopResults(status: Seq[(User,Int)])
}

class Tally extends Actor with ActorLogging {
  import TallyProtocol._
  import resource._

  private var tally: Map[User,Int] = Map.empty
  private var last: String = ""

  val file = "/tmp/"+classOf[Tally].getCanonicalName

  override def preStart() {
    super.preStart()
    if(new File(file).exists()) {
      val result = managed(new ObjectInputStream((new FileInputStream(file)))) map {
        input =>
          input.readObject().asInstanceOf[Map[User,Int]]
      }
      tally = result.opt.getOrElse(Map.empty)
      log.info("read tally from " + file)
    }
  }

  override def postStop() {
    super.postStop()
    val out = new ObjectOutputStream(new FileOutputStream(file))
    out.writeObject(tally)
    out.close()
    log.info("wrote tally to " + file)
  }

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
  import TwitterFeedProtcol._
  import TallyProtocol._

  override def main(args: Array[String]) {
    implicit val timeout = Timeout(5 seconds)

    val config = ConfigFactory.load()
    implicit val actorSystem = ActorSystem("TwitterPopularity", config)

    val twitterConf = new ConfigurationBuilder()
      .setDebugEnabled(config.getBoolean("twitter.stream.debug"))
      .setOAuthConsumerKey(config.getString("twitter.oauth.consumerKey"))
      .setOAuthConsumerSecret(config.getString("twitter.oauth.consumerSecret"))
      .setOAuthAccessToken(config.getString("twitter.oauth.accessToken"))
      .setOAuthAccessTokenSecret(config.getString("twitter.oauth.accessTokenSecret"))
      .build()
    val twitterApi = new TwitterFactory(twitterConf).getInstance()

    val tallyActor = actorSystem.actorOf(Props[Tally], "Tally")
    val votersActor = actorSystem.actorOf(Props(new Voters(twitterApi, tallyActor))
      .withDispatcher("twitter.akka.stash-dispatcher"), "Voters")

    val twitterStream = new TwitterStreamFactory(twitterConf).getInstance()
    twitterStream.addListener(new TwitterStreamListener(votersActor))
    val streamActor = actorSystem.actorOf(Props(new TwitterStreamConsumer(twitterStream)), "Stream")

    sys.addShutdownHook({
      println("Shutdown")
      Await.result(streamActor ? Stop, 5 seconds)
      // Shut them down gracefully
      val stopped = List(tallyActor, votersActor, streamActor).map { a => gracefulStop(a, 5.seconds) }
      Await.result(Future.sequence(stopped), 5.seconds)
      actorSystem.shutdown()
    })

    val track = args.headOption.getOrElse(config.getString("twitter.stream.track"))
    streamActor ! Start(track.split(",") :_*)

    // Print periodic results
    actorSystem.scheduler.schedule(10 seconds, 10 seconds) {
      tallyActor ! Top(10)
    }
  }
}