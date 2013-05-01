package org.example

import akka.actor.{ActorRef, Props, ActorSystem}
import akka.testkit.{TestProbe, TestKit, TestActorRef, ImplicitSender}
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.scalatest.{ParallelTestExecution, WordSpec, BeforeAndAfterAll}
import org.scalatest.matchers.ShouldMatchers
import twitter4j._
import org.mockito.ArgumentCaptor
import org.example.UserRegistryProtocol.AddUser

class TwitterFeedConsumerSpec extends TestKit(ActorSystem("TwitterFeedSpec"))
  with ImplicitSender
  with WordSpec
  with ShouldMatchers
  with BeforeAndAfterAll
  with MockitoSugar
  with ParallelTestExecution {

  import TwitterFeedProtcol._

  override def afterAll() = system.shutdown()

  val mockTwitterFeed = mock[TwitterStream]

  def twitterFeedConsumer(implicit destActor: ActorRef = TestProbe().ref) = TestActorRef(Props(new TwitterFeedConsumer(mockTwitterFeed, destActor)))

  "TwitterFeedConsumer" when {

    "initialized" should {
      "be inactive" in {
        twitterFeedConsumer ! Busy_?
        expectMsg(Stopped)
      }
    }

    "stopped" should {
      "do nothing for a Stop message" in {
        val actorRef = twitterFeedConsumer
        actorRef ! Stop
        actorRef ! Busy_?
        expectMsg(Stopped)
      }
      "begin reading Statuses with a hashtag from a Twitter stream after a Start message" in {
        val track = "#foo"
        val actorRef = twitterFeedConsumer
        actorRef ! Start(track)
        actorRef ! Busy_?
        expectMsg(Streaming(track))

        verify(mockTwitterFeed).addListener(anyObject[StatusListener])
        verify(mockTwitterFeed).filter(new FilterQuery(0, Array.empty, Array(track)))
      }
    }

    "streaming" should {
      "send the User for a Status to the UserRegistry" in {
        val probe = TestProbe()
        val actorRef = twitterFeedConsumer(probe.ref)
        actorRef ! Start("track")

        // hijack the StatusListener and invoke it directly
        val argCaptor = ArgumentCaptor.forClass(classOf[StatusListener])
        verify(mockTwitterFeed).addListener(argCaptor.capture())
        val dummyStatus = mock[Status]
        when(dummyStatus.getUser) thenReturn mock[User]
        argCaptor.getValue.onStatus(dummyStatus)

        probe.expectMsg(AddUser(dummyStatus.getUser))
      }
      "stop reading the stream after a Stop message" in {
        val actorRef = twitterFeedConsumer
        actorRef ! Stop
        actorRef ! Busy_?
        expectMsg(Stopped)
      }
    }
  }
}