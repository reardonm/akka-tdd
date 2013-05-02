package org.example

import akka.actor._
import akka.pattern.{ ask, pipe }
import akka.dispatch.Await
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import twitter4j.conf.ConfigurationBuilder
import twitter4j._
import scala.collection.JavaConversions._
import org.example.TwitterPopularityProtocol.VoteFor


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
  case class AddUser(user: User)
}

class UserRegistry(twitter: Twitter, twitterPopularity: ActorRef) extends Actor {
  import UserRegistryProtocol._
  import TwitterPopularityProtocol._

  var users: Map[Long,User] = Map.empty

  def receive = {
    case AddUser(user) =>
      if (!users.contains(user.getId)) {
        val ids = twitter.getFriendsIDs(user.getId, 0)  // TODO: cursor?
        ids.getIDs.foreach(i => twitterPopularity ! VoteFor(i))
        users = users + (user.getId -> user)
      }
  }
}

object TwitterPopularityProtocol {
  case class VoteFor(id: Long)
}

class TwitterPopularity extends Actor {
  def receive = {
    case VoteFor(id) =>
      println(id)
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
    val statusListener = actorSystem.actorOf(Props(new UserRegistry(twitterApi, twitterPopularity)))
    val streamConsumer = actorSystem.actorOf(Props(new TwitterFeedConsumer(twitterStream, statusListener)))

    sys.addShutdownHook({
      println()
      Await.result(streamConsumer ? Stop, 3 seconds)
      actorSystem.shutdown()
    })

    streamConsumer ! Start(config.getString("twitter.stream.track"))
  }
}