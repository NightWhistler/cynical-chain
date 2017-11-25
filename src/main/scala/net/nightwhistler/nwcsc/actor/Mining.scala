package net.nightwhistler.nwcsc.actor

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props, Terminated}
import com.typesafe.scalalogging.Logger
import net.nightwhistler.nwcsc.actor.BlockChainActor._
import net.nightwhistler.nwcsc.actor.Mining.{BlockChainChanged, MineBlock, MineResult}
import net.nightwhistler.nwcsc.actor.MiningWorker.StopMining
import net.nightwhistler.nwcsc.actor.PeerToPeer.BroadcastRequest
import net.nightwhistler.nwcsc.blockchain.{Block, BlockChain, BlockMessage}
import akka.pattern.pipe
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

object Mining {

  case class MineBlock(blockChain: BlockChain, messages: Seq[BlockMessage] )

  case class BlockChainChanged( blockchain: BlockChain )

  case class MineResult(block: Block)

  def props( peerToPeer: ActorRef )(implicit ec: ExecutionContext)=
    Props( new Mining(peerToPeer))
}

class Mining( peerToPeer: ActorRef )(implicit ec: ExecutionContext) extends Actor {

  implicit val timeout = Timeout(Duration(5, TimeUnit.SECONDS))

  var miners: Set[ActorRef] = Set.empty
  var messages: Set[BlockMessage] = Set.empty

  def createWorker( factory: ActorRefFactory ): ActorRef = factory.actorOf(MiningWorker.props(peerToPeer))

  val logger = Logger(classOf[Mining])

  override def receive = {

    case BlockChainChanged(newBlockChain) =>
      logger.debug("The blockchain has changed, stopping all miners.")
      miners.foreach( _ ! StopMining )

      messages = messages.filterNot( newBlockChain.contains(_))

      if ( ! messages.isEmpty ) {
        self ! MineBlock(newBlockChain, messages.toSeq)
      }

    case MineBlock(blockChain, requestMessages) =>

      logger.debug(s"Got mining request for ${requestMessages.size} messages with current blockchain index at ${blockChain.latestBlock.index}")
      val filtered = requestMessages.filterNot( blockChain.contains(_))

      //We only need to start mining if any new messages are in the message.
      if ( ! (filtered.toSet -- messages).isEmpty ) {
        messages ++= filtered

        //Tell all peers to start mining
        peerToPeer! BroadcastRequest(AddMessages(messages.toSeq))

        //Spin up a new actor to do the mining
        val miningActor = createWorker(context)
        context.watch(miningActor)
        miners += miningActor

        miningActor ! MiningWorker.MineBlock(blockChain, messages.toSeq)
      } else logger.debug("Request contained no new messages, so not doing anything.")


    case Terminated(deadActor) =>
      miners -= deadActor
      logger.debug(s"Still running ${miners.size} miners for ${messages.size} messages")

      if ( miners.size == 0  && ! messages.isEmpty ) {
        val savedMessaged = messages.toSeq
        messages = Set.empty
        ( peerToPeer ? GetBlockChain ).mapTo[CurrentBlockChain]
          .map(r => MineBlock(r.blockChain, savedMessaged) ) pipeTo self
      }
  }

}