package org.example

import akka.testkit.TestActorRef
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.scalatest.{ParallelTestExecution, WordSpec, BeforeAndAfterAll}
import org.scalatest.matchers.ShouldMatchers
import twitter4j._
import akka.actor.Props

class TwitterFeedConsumerSpec extends WordSpec
  with ShouldMatchers
  with BeforeAndAfterAll
  with MockitoSugar
  with ParallelTestExecution {

  import TwitterFeedProtcol._

  val mockTwitterFeed = mock[TwitterStream]

  "TwitterStreamConsumer" when {
    "stopped" should {
      "ignore a Stop message" in new ActorSystemFixture {
        val actorRef = TestActorRef(Props(new TwitterStreamConsumer(mockTwitterFeed)))
        actorRef ! Busy_?
        expectMsg(Stopped)
        actorRef ! Stop
        expectNoMsg()
        actorRef ! Busy_?
        expectMsg(Stopped)
      }

      "begin streaming Tweets with the filter from a Start message" in new ActorSystemFixture {
        val actorRef = TestActorRef(Props(new TwitterStreamConsumer(mockTwitterFeed)))
        actorRef ! Busy_?
        expectMsg(Stopped)
        val filter = List("#foo","#bar")
        actorRef ! Start(filter)
        expectMsg(Streaming(filter))
        actorRef ! Busy_?
        expectMsg(Streaming(filter))
        verify(mockTwitterFeed).filter(new FilterQuery(0, Array.empty, filter.toArray))
      }
    }

    "streaming" should {
      "stop reading the stream after a Stop message" in new ActorSystemFixture {
        val actorRef = TestActorRef(Props(new TwitterStreamConsumer(mockTwitterFeed)))
        actorRef ! Stop
        actorRef ! Busy_?
        expectMsg(Stopped)
      }
    }
  }
}