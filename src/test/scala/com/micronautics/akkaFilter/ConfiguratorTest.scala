package com.micronautics.akkaFilter

import akka.actor._
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import _root_.com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.{BeforeAndAfterAll, WordSpec}
import akka.util.duration._
import collection.mutable

/**
 * From the [`TestKit` docs](http://doc.akka.io/docs/akka/snapshot/scala/testing.html):
 * ''The TestKit contains an actor named testActor which is the entry point for messages to be examined with the various
 * expectMsg... assertions. When mixing in the trait ImplicitSender this test actor is implicitly used as sender
 * reference when dispatching messages from the test procedure.''
 * @author Mike Slinn
 */
class ConfiguratorTest(_system: ActorSystem) extends TestKit(_system)
  with ImplicitSender with ShouldMatchers with WordSpec with BeforeAndAfterAll {

  lazy val filterActor2a = new FilterActor((msg: String) =>
      msg.reverse.substring(msg.length/2),
    (msg: String) =>
      msg.contains("1"))

  lazy val filterActor1a = new FilterActor((msg: String) =>
      msg.reverse,
    (msg: String) =>
      msg.contains("1"))

  lazy val filterActor1b = new FilterActor((msg: String) =>
      msg.substring(msg.length/2),
    (msg: String) =>
      msg.contains("1"))

  lazy val predicateActor1 = new PredicateActor[String]((msg: String) =>
      msg.contains("1"))

  lazy val predicateActor2 = new PredicateActor[String]((msg: String) =>
      !msg.contains("1"))

  val predicateActor1Ref: ActorRef = system.actorOf(Props(predicateActor1), "predicateActor1")
  val predicateActor2Ref: ActorRef = system.actorOf(Props(predicateActor2), "predicateActor2")
  val filterActor1aRef: ActorRef = system.actorOf(Props(filterActor1a), "filterActor1a")
  val filterActor1bRef: ActorRef = system.actorOf(Props(filterActor1b), "filterActor1b")
  val filterActor2aRef: ActorRef = system.actorOf(Props(filterActor2a), "filterActor2a")
  val destinationActorRef: ActorRef = system.actorOf(Props[DestinationActor], "destinationActor")

  predicateActor1Ref ! NextActor("testActor")
  predicateActor2Ref ! NextActor("testActor")
  filterActor1aRef   ! NextActor("testActor")
  filterActor1bRef   ! NextActor("testActor")
  filterActor2aRef   ! NextActor("testActor")

  def this() = this {
    val logLevelStr = """akka.loglevel = "DEBUG" """.stripMargin
    val stringConf        = ConfigFactory.parseString(logLevelStr)
    val configApplication = ConfigFactory.load("application.conf")
    val configFilter      = ConfigFactory.load("akkaFilter.conf")
    val configDefault     = ConfigFactory.load
    val config            = ConfigFactory.load(stringConf.withFallback(configApplication)
                                                         .withFallback(configFilter)
                                                         .withFallback(configDefault))
    ActorSystem("default", config)
  }

  override def afterAll {
    system.shutdown()
  }

  "A Configuration" should {
    "use correct configuration value from layered settings" in {
      val debug = system.settings.config.getString("akka.loglevel")
      expect("debug", "")(debug.toLowerCase)

      val configFilter = ConfigFactory.load("akkaFilter.conf")
      expect("com.micronautics.akkaFilter.DestinationActor", "destinationActor address")(configFilter.getString("destinationActor"))

      val filterChains = configFilter.getObject("filterChains")
      expect(2, "filterChains size")(filterChains.size)

      val filterChain1 = configFilter.getStringList("filterChains.filter1")
      expect(3, "filterChain1 size")(filterChain1.size)
      val filterChain2 = configFilter.getStringList("filterChains.filter2")
      expect(2, "filterChain2 size")(filterChain2.size)
    }
  }

  "A FilterActor" should {
    "discard the appropriate messages and forward the rest" in {
      filterActor1aRef ! "message1"
      expectMsg("1egassem")

      filterActor1bRef ! "message1"
      expectMsg("age1")

      filterActor2aRef ! "message1"
      expectNoMsg(250 millis)
    }
  }

  "A PredicateActor" should {
    "select the correct filter chains" in {
      predicateActor1Ref ! "message1"
      expectMsg("message1")

      predicateActor2Ref ! "message1"
      expectNoMsg(250 millis)

      predicateActor2Ref ! "message2"
      expectMsg("message2")
    }
  }

  "An AkkaFilterContainer" should {
    "set up filter chains" in {
      // Creating this actor causes the other actors to get wired up:
      val akkaFilterContainerActorRef: ActorRef = system.actorOf(Props[AkkaFilterContainerActor], "akkaFilterContainerActor")

      val destinationActorRef = TestActorRef[DestinationActor]
      val destinationActor = destinationActorRef.underlyingActor
      destinationActor.lastMessage.clear()

      akkaFilterContainerActorRef ! "message1"
      expect(1, "")(destinationActor.lastMessage.size)
      assert(destinationActor.lastMessage.contains("message1"))

      akkaFilterContainerActorRef ! "message2"
      expect(1, "")(destinationActor.lastMessage.size)
      assert(destinationActor.lastMessage.contains("message1"))
    }
  }
}

class DestinationActor extends Actor {
  var lastMessage = mutable.ListBuffer.empty[String]

  def receive: Receive = {
    case msg: String =>
      lastMessage += msg
      println("DestinationActor got " + msg)
  }
}
