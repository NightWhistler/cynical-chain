package net.nightwhistler.nwcsc.actor

import akka.actor.Props
import com.typesafe.scalalogging.Logger
import net.nightwhistler.nwcsc.actor.MiningWorker.StopMining
import net.nightwhistler.nwcsc.blockchain.BlockChainCommunication.{ResponseBlock, ResponseBlockChain}
import net.nightwhistler.nwcsc.blockchain.Mining.BlockChainInvalidated
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
      case ResponseBlock(_) | ResponseBlockChain(_) => self ! BlockChainInvalidated
      case _ => //Do nothing
    }

    super[PeerToPeer].broadcast(message)
  }
}





