package com.micronautics.akkaFilter

import akka.actor.{Props, ActorRef, ActorLogging, Actor}
import com.typesafe.config.ConfigFactory
import scala.collection.JavaConversions._
import java.util.ArrayList
import scala.collection._

/**
  * @author Mike Slinn
  */
class AkkaFilterContainerActor extends Actor with ActorLogging {
  val config = ConfigFactory.load("akkaFilter.conf")

  val destinationActor = config.getString("destinationActor")
  val destinationActorRef = context.system.actorFor(destinationActor)

  val filterChainsConfig = config.getObject("filterChains")
  val filterChainMap = filterChainsConfig.map { case (chainName, value) =>
    val actorNameChain = value.unwrapped().asInstanceOf[ArrayList[String]]
    // Let creation of the actors be the responsibility of the caller:
    // actorNameChain.foreach { actorName => context.system.actorOf(Props(actorName), actorName) }
    actorNameChain.foreach { actorName =>
      val i = actorNameChain.indexOf(actorName)
      val nextActor = if (i==actorNameChain.length-1) destinationActor else actorNameChain.get(i)
      context.system.actorFor(actorName) ! NextActor(nextActor)
    }
    chainName -> actorNameChain
  }.toMap
  // todo verify that each chain consists of a PredicateActor followed by one or more FilterActors

  def process(msg: Any) {

  }

  def receive: Receive = {
    case _ =>
      // todo for each chain, test predicate then send message to first actor in chain if predicate passes
  }
}

object AkkaFilterContainerActor extends App {

}

/**
  * @param predicate must evaluate true when invoked on any message passed to this actor in order for the message to be forwarded
  * @param nextActor might be a filter or the destination actor; this actor does not know the difference
  * If there are no filters then forward messages that pass the predicate to nextActor, which should be the destination
  */
class PredicateActor[T](predicate: T => Boolean) extends Actor with ActorLogging {
  /** nextActor might be a filter or the destination actor; this actor does not know the difference */
  var nextActor: Option[ActorRef] = None

  def receive = {
    case next: NextActor =>
      nextActor = Some(context.system.actorFor(next.actorName))

    case msg: T =>
      if (predicate(msg)) {
        nextActor match {
          case Some(actorRef) =>
            actorRef.forward(msg)

          case None =>
            log.warning("%s does not have a nextActor defined; message not filtered or forwarded".format(this.toString))
        }
      } else
        log.debug("%s dropping '%s'".format(getClass.getName, msg.toString))

    case other =>
      log.warning("%s got a %s".format(getClass.getName, other.toString))
  }
}

case class NextActor(actorName: String)

/**
  * @param transformation is invoked on any message passed to this actor before testing predicate
  * @param predicate must evaluate true when invoked on a transformed message in order for the message to be forwarded
  */
class FilterActor[T, U](transformation: T => U, predicate: U => Boolean) extends Actor with ActorLogging {
  /** nextActor might be a filter or the destination actor; this actor does not know the difference */
  var nextActor: Option[ActorRef] = None

  def receive = {
    case next: NextActor =>
      nextActor = Some(context.system.actorFor(next.actorName))

    case msg: T =>
      val newMsg: U = transformation(msg)
      if (predicate(newMsg)) {
        nextActor match {
          case Some(actorRef) =>
            actorRef.forward(newMsg)

          case None =>
            log.warning("%s does not have a nextActor defined; message filtered but not forwarded".format(this.toString))
        }
      } else
        log.debug("%s dropping '%s' after transforming to '%s'".format(getClass.getName, msg.toString, newMsg.toString))
  }
}
