package org.example

import org.scalatest.{WordSpec, BeforeAndAfterAll}
import org.scalatest.matchers.ShouldMatchers
import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestKit, TestActorRef, ImplicitSender}
import akka.dispatch.Await

class TwitterFeedConsumerSpec extends TestKit(ActorSystem("TwitterFeedSpec"))
  with ImplicitSender
  with WordSpec
  with ShouldMatchers
  with BeforeAndAfterAll {

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
      "do nothing for stop message" in pending
      "change to reading state for hashtag message" in  pending
    "reading" should {
      "send user for each tweet to the UserRegistry actor" in pending
      }

      "change to stopped state for stop message" in pending
    }
  }
}