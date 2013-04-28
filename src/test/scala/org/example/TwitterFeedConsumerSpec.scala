package org.example

import org.scalatest._


class TwitterFeedConsumerSpec  extends WordSpec
  with MustMatchers
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