package org.example

import akka.actor._
import akka.pattern.{ ask, pipe }
import akka.dispatch.Await
import com.typesafe.config.ConfigFactory
import twitter4j.conf.ConfigurationBuilder
import twitter4j._
import akka.util.Timeout
import twitter4j.Status


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

class UserRegistry extends Actor {
  import UserRegistryProtocol._

  var users: Map[Long,User] = Map.empty
  def hasUser(user: User) = users.contains(user.getId)

  def receive = {
    case AddUser(user) => users = users + (user.getId -> user)
  }
}


object TwitterPopularity extends App {
  import akka.util.duration._
  import TwitterFeedProtcol._

  override def main(args: Array[String]) {
    implicit val timeout = Timeout(5 seconds)

    val config = ConfigFactory.load()
    val actorSystem = ActorSystem("TwitterPopularity", config)

    val t4jConfBuilder = new ConfigurationBuilder()
      .setDebugEnabled(config.getBoolean("twitter.stream.debug"))
      .setOAuthConsumerKey(config.getString("twitter.oauth.consumerKey"))
      .setOAuthConsumerSecret(config.getString("twitter.oauth.consumerSecret"))
      .setOAuthAccessToken(config.getString("twitter.oauth.accessToken"))
      .setOAuthAccessTokenSecret(config.getString("twitter.oauth.accessTokenSecret"))
    val twitterStream = new TwitterStreamFactory(t4jConfBuilder.build()).getInstance()

    val statusListener = actorSystem.actorOf(Props[UserRegistry])
    val streamConsumer = actorSystem.actorOf(Props(new TwitterFeedConsumer(twitterStream, statusListener)))

    sys.addShutdownHook({
      println()
      Await.result(streamConsumer ? Stop, 3 seconds)
      actorSystem.shutdown()
    })

    streamConsumer ! Start(config.getString("twitter.stream.track"))
  }
}