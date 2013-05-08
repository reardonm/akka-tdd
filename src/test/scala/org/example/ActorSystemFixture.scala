package org.example

import akka.testkit.{TestKit, ImplicitSender}
import akka.actor.ActorSystem
import java.util.concurrent.atomic.AtomicInteger

object ActorSystemFixture {
  val sysId = new AtomicInteger()
}

/**
 * Provides a TestKit ActorSystem to get isolation between tests
 */
class ActorSystemFixture(name: String) extends TestKit(ActorSystem(name)) with ImplicitSender with DelayedInit {

  def this() = this("TestSystem-" + ActorSystemFixture.sysId.incrementAndGet())

  def delayedInit(f: => Unit) = {
    try {
      f
    } finally {
      system.shutdown()
    }
  }
}