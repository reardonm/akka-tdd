package org.example

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestKit, TestActorRef, ImplicitSender}
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.scalatest.{ParallelTestExecution, WordSpec, BeforeAndAfterAll}
import org.scalatest.matchers.ShouldMatchers
import twitter4j.{FilterQuery, StatusListener, TwitterStream}

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


  "TwitterFeedConsumer" when {

    "initialized" should {
      "be inactive" in {
        val actorRef = TestActorRef(Props(new TwitterFeedConsumer(mockTwitterFeed)))
        actorRef ! Busy_?
        expectMsg(Stopped)
      }
    }

    "stopped" should {
      "do nothing for stop message" in {
        val actorRef = TestActorRef(Props(new TwitterFeedConsumer(mockTwitterFeed)))
        actorRef ! Stop
        actorRef ! Busy_?
        expectMsg(Stopped)
      }
      "change to reading state for hashtag message" in {
        val hashtag = "#foo"
        val actorRef = TestActorRef(Props(new TwitterFeedConsumer(mockTwitterFeed)))
        actorRef ! Start(hashtag)
        actorRef ! Busy_?
        expectMsg(Streaming(hashtag))

        verify(mockTwitterFeed).addListener(anyObject[StatusListener])
        verify(mockTwitterFeed).filter(new FilterQuery(0, Array.empty, Array(hashtag)))
      }
    }

    "reading" should {
      "send user for each tweet to the UserRegistry actor" in pending
      "change to stopped state for stop message" in pending
    }
  }
}