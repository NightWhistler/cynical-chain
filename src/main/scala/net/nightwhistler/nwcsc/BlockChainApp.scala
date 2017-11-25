package net.nightwhistler.nwcsc

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import net.nightwhistler.nwcsc.actor.{BlockChainActor, PeerToPeer}
import net.nightwhistler.nwcsc.actor.PeerToPeer.AddPeer
import net.nightwhistler.nwcsc.blockchain.BlockChain
import net.nightwhistler.nwcsc.rest.RestInterface

object BlockChainConfig {
  val config = ConfigFactory.load()

  val seedHost = config.getString("blockchain.seedHost")
  val nodeName = config.getString("blockchain.nodeName")

  val httpInterface = config.getString("http.interface")
  val httpPort = config.getInt("http.port")
}

object BlockChainApp extends App with RestInterface {

  import BlockChainConfig._
  val logger = Logger("WebServer")

  implicit val system = ActorSystem("BlockChain")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val peerToPeerActor = system.actorOf(PeerToPeer.props, "peerToPeerActor")

  logger.info(s"Node ${nodeName} coming online.")

  if ( ! seedHost.isEmpty ) {
    logger.info(s"Attempting to connect to seed-host ${seedHost}")
    peerToPeerActor ! AddPeer(seedHost)
  } else {
    logger.info("No seed host configured, waiting for messages.")
  }

  Http().bindAndHandle(routes, httpInterface, httpPort)
}
