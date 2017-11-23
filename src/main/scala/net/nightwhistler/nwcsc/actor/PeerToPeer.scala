package net.nightwhistler.nwcsc.actor

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props, Terminated}
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import net.nightwhistler.nwcsc.actor.PeerToPeer._
import akka.pattern.pipe

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

/**
  * Created by alex on 17-6-17.
  */
object PeerToPeer {

  case class AddPeer( address: String )

  case class ResolvedPeer( actorRef: ActorRef )

  case class Peers( peers: Seq[String] )

  case object GetPeers

  case object HandShake

  case class BroadcastRequest(message: Any)

  def props(implicit ec: ExecutionContext) = Props(new PeerToPeer)
}

class PeerToPeer(implicit ec: ExecutionContext) extends Actor {

  implicit val timeout = Timeout(Duration(5, TimeUnit.SECONDS))
  implicit val executionContext = context.system.dispatcher

  val logger: Logger = Logger(classOf[PeerToPeer])
  var peers: Set[ActorRef] = Set.empty

  def broadcast( message: Any ) = peers.foreach( _ ! message )

  override def receive = {

    case BroadcastRequest(message) => broadcast(message)

    case AddPeer(peerAddress) =>
      logger.debug(s"Got request to add peer ${peerAddress}")
      context.actorSelection(peerAddress).resolveOne().map( ResolvedPeer(_) ).pipeTo(self)

    case ResolvedPeer(newPeerRef: ActorRef) =>

      if ( ! peers.contains(newPeerRef) ) {
        context.watch(newPeerRef)

        //Introduce ourselves
        newPeerRef ! HandShake

        //Ask for its friends
        newPeerRef ! GetPeers

        //Tell our existing peers
        broadcast(AddPeer(newPeerRef.path.toSerializationFormat))

        //Add to the current list of peers
        peers += newPeerRef
      } else logger.debug("We already know this peer, discarding")

    case Peers(peers) => peers.foreach( self ! AddPeer(_))

    case HandShake =>
      logger.debug(s"Received a handshake from ${sender().path.toStringWithoutAddress}")
      peers += sender()

    case GetPeers => sender() ! Peers(peers.toSeq.map(_.path.toSerializationFormat))

    case Terminated(actorRef) =>
      logger.debug(s"Peer ${actorRef} has terminated. Removing it from the list.")
      peers -= actorRef

  }

}
