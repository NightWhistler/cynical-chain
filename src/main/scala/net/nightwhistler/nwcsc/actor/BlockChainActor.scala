package net.nightwhistler.nwcsc.actor

import akka.actor.Props
import com.typesafe.scalalogging.Logger
import net.nightwhistler.nwcsc.actor.MiningActor.StopMining
import net.nightwhistler.nwcsc.blockchain.BlockChainCommunication.ResponseBlock
import net.nightwhistler.nwcsc.blockchain.{BlockChain, BlockChainCommunication, Mining}
import net.nightwhistler.nwcsc.p2p.PeerToPeer

object BlockChainActor {
  def props( blockChain: BlockChain ): Props = Props(classOf[BlockChainActor], blockChain)
}

class BlockChainActor( var blockChain: BlockChain ) extends CompositeActor with PeerToPeer
  with BlockChainCommunication with Mining {
  override val logger = Logger("BlockChainActor")

  override def broadcast(message: Any): Unit = {
    message match {
      case ResponseBlock(newBlock) =>
        if ( miners.contains(newBlock.message) ) {
          logger.debug(s"We have a new block for ${newBlock.message}, but we're still mining it. Stopping miner.")
          miners(newBlock.message) ! StopMining
        }
      case _ => //Do nothing
    }

    super[PeerToPeer].broadcast(message)
  }
}





