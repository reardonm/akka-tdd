package org.example

import org.scalatest.{ParallelTestExecution, WordSpec, BeforeAndAfterAll}
import org.scalatest.matchers.ShouldMatchers
import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestKit, TestActorRef, ImplicitSender}

class TwitterFeedConsumerSpec extends TestKit(ActorSystem("TwitterFeedSpec"))
  with ImplicitSender
  with WordSpec
  with ShouldMatchers
  with BeforeAndAfterAll
  with ParallelTestExecution {

  import TwitterFeedProtcol._

  override def afterAll() = system.shutdown()

  "TwitterFeedConsumer" when {

    "initialized" should {
      "be inactive" in {
        val actorRef = TestActorRef[TwitterFeedConsumer]
        actorRef ! Busy_?
        expectMsg(No)
      }
    }

    "stopped" should {
      "do nothing for stop message" in {
        val actorRef = TestActorRef[TwitterFeedConsumer]
        actorRef ! Stop
        actorRef ! Busy_?
        expectMsg(No)
      }
      "change to reading state for hashtag message" in {
        val hashtag = "foo"
        val actorRef = TestActorRef[TwitterFeedConsumer]
        actorRef ! Start(hashtag)
        actorRef ! Busy_?
        expectMsg(Yes(hashtag))
      }
    }

    "reading" should {
      "send user for each tweet to the UserRegistry actor" in pending
      "change to stopped state for stop message" in pending
    }
  }
}