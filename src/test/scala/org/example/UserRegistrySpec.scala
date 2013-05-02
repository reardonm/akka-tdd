package org.example

import org.scalatest._
import org.scalatest.matchers.ShouldMatchers
import akka.testkit.{TestProbe, TestActorRef, ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import akka.util.duration._
import org.scalatest.mock.MockitoSugar
import twitter4j.{IDs, Twitter, User}
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.example.UserRegistryProtocol.AddUser
import org.example.TwitterPopularityProtocol.VoteFor

class UserRegistrySpec  extends TestKit(ActorSystem("UserRegistrySpec"))
  with ImplicitSender
  with WordSpec
  with ShouldMatchers
  with BeforeAndAfterAll
  with MockitoSugar
  with ParallelTestExecution {


  def dummyUser(id: Long) = {
    val u = mock[User]
    when(u.getId) thenReturn id
    u
  }

  "UserRegistry" should {
    "Send followed Users to TwitterPopularity for each unique AddUser message" in {
      val userId1 = 100L
      val userId2 = 200L
      val userId3 = 300L

      val u2friends = mock[IDs]
      when(u2friends.getIDs) thenReturn (Array(userId1,userId3))

      val u1friends = mock[IDs]
      when(u1friends.getIDs) thenReturn (Array(userId2,userId3))

      val twitter = mock[Twitter]
      when(twitter.getFriendsIDs(userId1, 0L)) thenReturn (u1friends)
      when(twitter.getFriendsIDs(userId2, 0L)) thenReturn (u2friends)

      val testProbe = TestProbe()
      val actorRef = TestActorRef(Props(new UserRegistry(twitter, testProbe.ref)))

      val actor = actorRef.underlyingActor.asInstanceOf[UserRegistry]
      actor.users should be ('empty)
      actorRef ! AddUser(dummyUser(userId1))
      actorRef ! AddUser(dummyUser(userId2))
      actorRef ! AddUser(dummyUser(userId1))

      testProbe.expectMsgAllOf(VoteFor(userId1),VoteFor(userId2),VoteFor(userId3),VoteFor(userId3))

      actor.users should contain key (userId2)
      actor.users.size should be (2)
    }
  }
}
