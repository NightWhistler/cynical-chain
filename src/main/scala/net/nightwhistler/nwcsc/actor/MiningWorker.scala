package net.nightwhistler.nwcsc.actor

import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive
import com.typesafe.scalalogging.Logger
import net.nightwhistler.nwcsc.BlockChainConfig
import net.nightwhistler.nwcsc.actor.MiningActor.MineResult
import net.nightwhistler.nwcsc.actor.MiningWorker.MineBlock
import net.nightwhistler.nwcsc.blockchain.{BlockChain, BlockMessage}

import scala.collection.immutable.Seq

import scala.util.{Failure, Success}

object MiningWorker {
  case class MineBlock(blockChain: BlockChain, messages: Seq[BlockMessage],
                       startnonce: Long = 0, timeStamp: Long = new java.util.Date().getTime )

  def props( reportBackTo: ActorRef): Props = Props(new MiningWorker(reportBackTo))
}

class MiningWorker(reportBackTo: ActorRef) extends Actor {

  val logger = Logger(classOf[MiningWorker])
  val nodeName = BlockChainConfig.nodeName

  override def receive: Receive = LoggingReceive {

    case MineBlock(blockChain, messages, nonce, timeStamp) =>
      if ( nonce == 0 ) {
        logger.debug(s"Starting mining attempt for ${messages.size} messages with index ${blockChain.head.index +1}")
      }

      val newBlock = blockChain.generateNextBlock(messages, nodeName, nonce)
      val hash = blockChain.hashFunction(newBlock)

      if ( hash < blockChain.difficultyFunction(newBlock)) {
        logger.debug(s"Found a block with index ${newBlock.index} after ${new java.util.Date().getTime - timeStamp} ms and ${newBlock.nonce +1} attempts.")
        reportBackTo ! MineResult(newBlock)
        context.stop(self)
      } else {
        self ! MineBlock(blockChain, messages, nonce + 1, timeStamp)
      }
  }

  override def postStop(): Unit = logger.debug("Aborted mining.")
}
