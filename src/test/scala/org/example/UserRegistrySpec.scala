package org.example

import org.scalatest._
import org.scalatest.matchers.ShouldMatchers
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import akka.util.duration._
import org.scalatest.mock.MockitoSugar
import twitter4j.User
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.example.UserRegistryProtocol.AddUser

class UserRegistrySpec  extends TestKit(ActorSystem("UserRegistrySpec"))
  with ImplicitSender
  with WordSpec
  with ShouldMatchers
  with BeforeAndAfterAll
  with MockitoSugar
  with ParallelTestExecution {


  "UserRegistry" should {
    "registar a user given in user message" in {
      val userId1 = 100L
      val userId2 = 200L
      val user1 = mock[User]
      val user2 = mock[User]
      when(user1.getId) thenReturn userId1
      when(user2.getId) thenReturn userId2

      val actorRef = TestActorRef(Props[UserRegistry])

      actorRef.underlyingActor.asInstanceOf[UserRegistry].users should be ('empty)
      actorRef ! AddUser(user1)
      actorRef ! AddUser(user2)
      actorRef ! AddUser(user1)

      Thread.sleep(500)
      actorRef.underlyingActor.asInstanceOf[UserRegistry].users should contain key (userId2)
      actorRef.underlyingActor.asInstanceOf[UserRegistry].users.size should be (2)
    }
  }
}
