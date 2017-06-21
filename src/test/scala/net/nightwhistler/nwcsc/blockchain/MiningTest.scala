package net.nightwhistler.nwcsc.blockchain

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import net.nightwhistler.nwcsc.actor.CompositeActor
import net.nightwhistler.nwcsc.blockchain.BlockChainCommunication.ResponseBlock
import net.nightwhistler.nwcsc.blockchain.Mining.MineBlock
import net.nightwhistler.nwcsc.p2p.PeerToPeer
import net.nightwhistler.nwcsc.p2p.PeerToPeer.{AddPeer, GetPeers, HandShake}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, GivenWhenThen}

/**
  * Created by alex on 20-6-17.
  */

class TestMiningActor extends CompositeActor with Mining with PeerToPeer with BlockChainCommunication {
  var blockChain = BlockChain()
}

class MiningTest extends TestKit(ActorSystem("BlockChain")) with FlatSpecLike
  with ImplicitSender with GivenWhenThen with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  trait WithMiningActor {
    val miningActor = system.actorOf(Props[TestMiningActor])
  }

  "A Mining actor" should "reply with the new block when a mining request is finished" in new WithMiningActor {

    miningActor ! HandShake
    val miningRequest = MineBlock(BlockMessage("testBlock"))
    miningActor ! miningRequest

    expectMsg(miningRequest)

    expectMsgPF() {
      case ResponseBlock(block) => assert(block.messages.data == "testBlock")
    }

  }

  it should "forward any mining requests to all peers" in new WithMiningActor {
    val probe = TestProbe()
    val blockMessage = BlockMessage("testBlock")
    miningActor ! AddPeer(probe.ref.path.toStringWithoutAddress)
    miningActor ! MineBlock(blockMessage)

    probe.expectMsg(HandShake)
    probe.expectMsg(GetPeers)
    probe.expectMsg(MineBlock(blockMessage))
  }




}
