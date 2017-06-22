package net.nightwhistler.nwcsc.blockchain

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActor, TestKit, TestProbe}
import net.nightwhistler.nwcsc.DummyDifficultyFunction
import net.nightwhistler.nwcsc.actor.MiningWorker.{MineResult, StopMining}
import net.nightwhistler.nwcsc.actor.{CompositeActor, MiningWorker}
import net.nightwhistler.nwcsc.blockchain.BlockChain.DifficultyFunction
import net.nightwhistler.nwcsc.blockchain.BlockChainCommunication.ResponseBlock
import net.nightwhistler.nwcsc.blockchain.Mining.{BlockChainInvalidated, MineBlock}
import net.nightwhistler.nwcsc.p2p.PeerToPeer
import net.nightwhistler.nwcsc.p2p.PeerToPeer.{AddPeer, GetPeers, HandShake}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, GivenWhenThen}

/**
  * Created by alex on 20-6-17.
  */

class TestMiningActor(dummyWorker: ActorRef, var blockChain: BlockChain) extends CompositeActor with Mining with PeerToPeer with BlockChainCommunication {
  override def createWorker(factory: ActorRefFactory): ActorRef = dummyWorker
}

object ImpossibleDifficultyFunction extends DifficultyFunction {
  override def apply(block: Block): BigInt = 0
}

class MiningTest extends TestKit(ActorSystem("BlockChain")) with FlatSpecLike
  with ImplicitSender with GivenWhenThen with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  trait WithMiningActor {
    val workerProbe = TestProbe()
    var blockChain = BlockChain(DummyDifficultyFunction)
    val miningActor = system.actorOf(Props(classOf[TestMiningActor], workerProbe.ref, blockChain))
  }

  "A Mining actor" should "reply with the new block when a mining request is finished" in new WithMiningActor {

    miningActor ! HandShake
    val miningRequest = MineBlock(Seq(BlockMessage("testBlock")))
    miningActor ! miningRequest

    expectMsg(miningRequest)

    workerProbe.setAutoPilot( (sender: ActorRef, msg: Any) => msg match {
      case MiningWorker.MineBlock(_, Seq(testMessage), 0, _) =>
        assert(testMessage.data == "testBlock")
        sender ! MineResult(blockChain.addMessage("testBlock").latestBlock)
        TestActor.NoAutoPilot
    })

    expectMsgPF() {
      case ResponseBlock(block) => assert(block.messages(0).data == "testBlock")
    }

  }

  it should "send all outstanding messages to a new worker" in new WithMiningActor {
    Given("a situation where 2 mining requests come in")

    When("the first request comes in")
    Then("a worker should be started for just that request")

    When("the second request comes in while the first isn't finished yet")
    Then("a new worker should be started which tries to add both messages")

    fail("not written")
  }

  it should "re-schedule all outstanding messages in case an outdated block is returned" in new WithMiningActor {
    When("a worker that returns a block that is no longer valid")

    Then("all the messages in the block that are not yet in the blockchain should be added to a new request")
    fail("not yet written")
  }

  it should "keep spawning workers until there is no more work to do" in new WithMiningActor {
    When("the last worker terminates, but there are still outstanding messaged")
    Then("a new worker should be spawned for all the remaining messages.")
    fail("not yet written")
  }

  it should "forward any mining requests to all peers" in new WithMiningActor {
    val probe = TestProbe()
    val blockMessages = Seq(BlockMessage("testBlock"))
    miningActor ! AddPeer(probe.ref.path.toStringWithoutAddress)
    miningActor ! MineBlock(blockMessages)

    probe.expectMsg(HandShake)
    probe.expectMsg(GetPeers)
    probe.expectMsg(MineBlock(blockMessages))
  }


  it should "stop all workers when the blockchain changes" in new WithMiningActor {
    val testMessage = BlockMessage("bla")
    miningActor ! MineBlock(Seq(testMessage))

    workerProbe.expectMsgPF() {
      case MiningWorker.MineBlock(_, Seq(testMessage), 0, _) => assert(testMessage.data == "bla")
    }

    miningActor ! BlockChainInvalidated

    workerProbe.expectMsg(StopMining)
  }

}
