package org.example

import akka.actor.{ActorLogging, Actor}


object TwitterPopularity extends App {
  println("Hello World")
}

object TwitterFeedProtcol {
  case class Start(hashTag: String)
  case object Stop
  case object Busy_?
  case object No
  case class Yes(hashTag: String)
}

class TwitterFeedConsumer extends Actor with ActorLogging {
  import TwitterFeedProtcol._

  def stopped: Receive = {
    case Start(hashTag) =>
      println("Start")
      context.become(started(hashTag))
    case Busy_? =>
      sender ! No
  }

  def started(hashTag: String): Receive = {
    case Stop =>
      println("Stop")
      context.become(stopped)
    case Busy_? =>
      sender ! Yes(hashTag)
  }

  def receive = stopped
}
