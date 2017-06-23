package net.nightwhistler.nwcsc.blockchain

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem, PoisonPill, Props, Terminated}
import akka.testkit.{ImplicitSender, TestActor, TestKit, TestProbe}
import net.nightwhistler.nwcsc.actor.MiningWorker.{MineResult, StopMining}
import net.nightwhistler.nwcsc.actor.{CompositeActor, MiningWorker}
import net.nightwhistler.nwcsc.blockchain.BlockChainCommunication.ResponseBlock
import net.nightwhistler.nwcsc.blockchain.Mining.{BlockChainInvalidated, MineBlock}
import net.nightwhistler.nwcsc.p2p.PeerToPeer
import net.nightwhistler.nwcsc.p2p.PeerToPeer.{AddPeer, GetPeers, HandShake, ResolvedPeer}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, GivenWhenThen, Matchers}

/**
  * Created by alex on 20-6-17.
  */

class TestMiningActor(dummyWorker: => ActorRef, var blockChain: BlockChain) extends CompositeActor with Mining with PeerToPeer with BlockChainCommunication {
  override def createWorker(factory: ActorRefFactory): ActorRef = dummyWorker
}

class MiningTest extends TestKit(ActorSystem("BlockChain")) with FlatSpecLike
  with ImplicitSender with GivenWhenThen with BeforeAndAfterAll with Matchers {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  trait WithMiningActor {
    var workerProbe = TestProbe()

    val probeFactory = { () => workerProbe.ref }
    var blockChain = BlockChain(DummyDifficulty)
    val miningActor = system.actorOf(Props(classOf[TestMiningActor], probeFactory, blockChain))
  }

  "A Mining actor" should "notify all peers with the new block when a mining request is finished" in new WithMiningActor {

    Given("a worker that immediately returns a block")
    workerProbe.setAutoPilot( (sender: ActorRef, msg: Any) => msg match {
      case MiningWorker.MineBlock(_, messages, 0, _) =>
        sender ! MineResult(blockChain.addBlock(messages).latestBlock)
        TestActor.NoAutoPilot
    })

    When("we register ourself as a peer")
    miningActor ! HandShake

    When("a mining request is sent")
    val miningRequest = MineBlock(Seq(BlockMessage("testBlock")))
    miningActor ! miningRequest


    Then("we expect to also get a mining request as a peer")
    expectMsg(miningRequest)

    Then("we expect to be notified when the block is found.")
    expectMsgPF() {
      case ResponseBlock(Block(_, _, _, Seq(BlockMessage(data, _)), _, _ )) => data shouldBe "testBlock"
    }

  }

  it should "send all outstanding messages to a new worker" in new WithMiningActor {
    Given("a situation where 2 mining requests come in")

    When("the first request comes in")
    miningActor ! MineBlock(Seq(BlockMessage("message 1")))
    Then("a worker should be started for just that request")
    workerProbe.expectMsgPF() {
      case MiningWorker.MineBlock(_, Seq(BlockMessage(data,_)), _, _ ) => data shouldBe "message 1"
    }

    When("the second request comes in while the first isn't finished yet")
    miningActor ! MineBlock(Seq(BlockMessage("message 2")))
    Then("a new worker should be started which tries to add both messages")
    workerProbe.expectMsgPF() {
      case MiningWorker.MineBlock(_, messages, _, _) =>
        val textMessages = messages.map( _.data )

        textMessages should have length 2
        textMessages should contain ("message 1")
        textMessages should contain ("message 2")
    }
  }

  ignore should "re-schedule all outstanding messages in case an outdated block is returned" in new WithMiningActor {
    Given("two mining requests")
    val originalMessage = BlockMessage("originalMessage")
    miningActor ! MineBlock(Seq(originalMessage))
    workerProbe.expectMsgPF() {
      case MiningWorker.MineBlock(_, Seq(msg), 0, _) => msg shouldEqual originalMessage
    }

    When("a new block comes in, updating the actor's blockchain")
    miningActor ! ResponseBlock(blockChain.addMessage("Some other message").latestBlock)

    When("a worker that returns a block that is no longer valid")
    miningActor ! MineResult(blockChain.addBlock(Seq(originalMessage)).latestBlock)

    Then("all the messages in the block that are not yet in the blockchain should be added to a new request")
    workerProbe.expectMsgPF() {
      case MiningWorker.MineBlock(_, Seq(msg), 0, _) => msg shouldEqual originalMessage
    }

  }

  it should "keep spawning workers until there is no more work to do" in new WithMiningActor {

    Given("we have a worker running")
    miningActor ! MineBlock(Seq(BlockMessage("Bla")))
    workerProbe.expectMsgPF() {
      case MiningWorker.MineBlock(_, Seq(BlockMessage(msg,_)), 0, _) => msg shouldEqual "Bla"
    }

    When("the last worker terminates, but there are still outstanding messaged")
    workerProbe.ref ! PoisonPill

    //Spawn a new workerprobe to be returned
    workerProbe = TestProbe()

    Then("a new worker should be spawned for all the remaining messages.")
    workerProbe.expectMsgPF() {
      case MiningWorker.MineBlock(_, Seq(BlockMessage(msg,_)), 0, _) => msg shouldEqual "Bla"
    }
  }

  it should "forward any mining requests to all peers" in new WithMiningActor {
    val probe = TestProbe()
    val blockMessages = Seq(BlockMessage("testBlock"))
    miningActor ! ResolvedPeer(probe.ref)
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
