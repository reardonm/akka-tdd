package org.example

import org.scalatest.{WordSpec, BeforeAndAfterAll}
import org.scalatest.matchers.ShouldMatchers
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}

class TwitterFeedConsumerSpec extends TestKit(ActorSystem("TwitterFeedSpec"))
  with ImplicitSender
  with WordSpec
  with ShouldMatchers
  with BeforeAndAfterAll {

  "TwitterFeedConsumer" when {

    "initialized" should {
      "be inactive" in pending
    }

    "stopped" should {
      "do nothing for stop message" in pending
      "change to reading state for hashtag message" in pending
     }

    "reading" should {
      "send user for each tweet to the UserRegistry actor" in pending
      "change to stopped state for stop message" in pending
    }
  }
}