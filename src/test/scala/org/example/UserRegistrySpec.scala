package org.example

import org.scalatest._
import org.scalatest.matchers.ShouldMatchers
import akka.testkit.{TestProbe, TestActorRef, ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import akka.util.duration._
import org.scalatest.mock.MockitoSugar
import twitter4j._
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.example.VotersProtocol._
import org.example.TallyProtocol._
import scala.collection.JavaConversions._
import org.example.VotersProtocol.AddUser
import org.example.TallyProtocol.VoteFor
import com.typesafe.config.ConfigFactory

class UserRegistrySpec  extends TestKit(ActorSystem("UserRegistrySpec",
  ConfigFactory.parseString("""test.stash-dispatcher = "akka.dispatch.UnboundedDequeBasedMailbox"""")))
  with ImplicitSender
  with WordSpec
  with ShouldMatchers
  with BeforeAndAfterAll
  with MockitoSugar {
  //with ParallelTestExecution {


  def dummyUser(id: Long) = {
    val u = mock[User]
    when(u.getId) thenReturn id
    u
  }

  val rateLimit = Map[String,RateLimitStatus]("/friends/list" -> mock[RateLimitStatus])

  val twitter = mock[Twitter]
  when(twitter.getRateLimitStatus("friends")) thenReturn rateLimit

  "Voters" when {
    "under Twitter rate-limits" should {
      "Send followed Users to Tally for each unique user" in {
        val userId1 = 100L
        val userId2 = 200L
        val userId3 = 300L

        val user1 = mock[User]
        when(user1.getId) thenReturn (userId1)
        val user2 = mock[User]
        when(user2.getId) thenReturn (userId2)
        val user3 = mock[User]
        when(user3.getId) thenReturn (userId3)

        val u1friends = mock[PagableResponseList[User]]
        when(u1friends.iterator()) thenReturn (List(user2,user3).iterator)

        val u2friends = mock[PagableResponseList[User]]
        when(u1friends.iterator()) thenReturn (List(user1,user3).iterator)

        when(twitter.getFriendsList(userId1, -1)) thenReturn (u1friends)
        when(twitter.getFriendsList(userId2, -1)) thenReturn (u2friends)

        val testProbe = TestProbe()
        val actorRef = TestActorRef(Props(new Voters(twitter, testProbe.ref))
          .withDispatcher("twitter.akka.stash-dispatcher"))

        val actor = actorRef.underlyingActor.asInstanceOf[Voters]
        actor.voters should be ('empty)
        actorRef ! AddUser(dummyUser(userId1))
        //expectMsg(RetrieveFriends(userId1))  // TODO why doesn't this work

        actorRef ! AddUser(dummyUser(userId2))
        //expectMsg(RetrieveFriends(userId2))

        actorRef ! AddUser(dummyUser(userId1))
        //expectNoMsg()

        //testProbe.expectMsgAllOf(VoteFor(userId1),VoteFor(userId2),VoteFor(userId3),VoteFor(userId3))
//        testProbe.expectMsgType[VoteFor] // TODO ick
//        testProbe.expectMsgType[VoteFor]
//        testProbe.expectMsgType[VoteFor]

        actor.voters should contain (userId2)
        actor.voters.size should be (2)
      }
      "Become inactive if over the rate-limit" in {
        val testProbe = TestProbe()
        val actorRef = TestActorRef(Props(new Voters(twitter, testProbe.ref)).withDispatcher("test.stash-dispatcher"))


      }
    }
    "over Twitter rate-limits" should {
      "Stash RetrieveFriends message" in pending
      "Schedule rate-limit check in Remaining seconds field" in pending
      "Become active if under rate-limit " in pending
    }
  }
}
