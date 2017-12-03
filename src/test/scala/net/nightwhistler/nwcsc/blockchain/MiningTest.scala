package net.nightwhistler.nwcsc.blockchain

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestActor, TestKit, TestProbe}
import net.nightwhistler.nwcsc.actor.BlockChainActor.{AddMessages, CurrentBlockChain, GetBlockChain, NewBlock}
import net.nightwhistler.nwcsc.actor.Mining.{BlockChainChanged, MineBlock, MineResult}
import net.nightwhistler.nwcsc.actor.PeerToPeer.BroadcastRequest
import net.nightwhistler.nwcsc.actor._
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, GivenWhenThen, Matchers}

import scala.concurrent.ExecutionContext


class TestMiningActor(dummyWorker: () => ActorRef, peerToPeer: ActorRef)(implicit ec: ExecutionContext) extends Mining( peerToPeer ) {
  override def createWorker(factory: ActorRefFactory): ActorRef = dummyWorker()
}

class MiningTest extends TestKit(ActorSystem("BlockChain")) with FlatSpecLike
  with ImplicitSender with GivenWhenThen with BeforeAndAfterAll with Matchers {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  trait WithMiningActor {
    import system.dispatcher

    val probeProvider = () => workerProbe.ref
    var workerProbe = TestProbe()

    var blockChain = BlockChain(NoDifficulty)
    val peerToPeerProbe = TestProbe()

    val miningActor = system.actorOf(Props(new TestMiningActor(probeProvider, peerToPeerProbe.ref)))
  }

  it should "send all outstanding messages to a new worker" in new WithMiningActor {
    Given("a situation where 2 mining requests come in")

    When("the first request comes in")
    miningActor ! MineBlock(blockChain, Seq(BlockMessage("message 1")))
    Then("a worker should be started for just that request")
    workerProbe.expectMsgPF() {
      case MiningWorker.MineBlock(_, Seq(BlockMessage(data,_)), _, _ ) => data shouldBe "message 1"
    }

    When("the second request comes in while the first isn't finished yet")
    miningActor ! MineBlock(blockChain, Seq(BlockMessage("message 2")))
    Then("a new worker should be started which tries to add both messages")
    workerProbe.expectMsgPF() {
      case MiningWorker.MineBlock(_, messages, _, _) =>
        val textMessages = messages.map( _.data )

        textMessages should have length 2
        textMessages should contain ("message 1")
        textMessages should contain ("message 2")
    }
  }

  it should "keep spawning workers until there is no more work to do" in new WithMiningActor {

    Given("we have a worker running")
    miningActor ! Mining.MineBlock(blockChain, Seq(BlockMessage("Bla")))
    peerToPeerProbe.expectMsgClass(classOf[BroadcastRequest])

    workerProbe.expectMsgPF() {
      case MiningWorker.MineBlock(_, Seq(BlockMessage(msg,_)), 0, _) => msg shouldEqual "Bla"
    }

    When("the last worker terminates, but there are still outstanding messaged")
    workerProbe.ref ! PoisonPill

    //Spawn a new workerprobe to be returned
    workerProbe = TestProbe()

    peerToPeerProbe.expectMsg(GetBlockChain)
    peerToPeerProbe.reply(CurrentBlockChain(blockChain))

    Then("a new worker should be spawned for all the remaining messages.")
    workerProbe.expectMsgPF() {
      case MiningWorker.MineBlock(_, Seq(BlockMessage(msg,_)), 0, _) => msg shouldEqual "Bla"
    }
  }

  it should "forward any mining requests to all peers" in new WithMiningActor {
    val probe = TestProbe()
    val blockMessages = Seq(BlockMessage("testBlock"))
    miningActor ! MineBlock(blockChain, blockMessages)

    peerToPeerProbe.expectMsg(BroadcastRequest(AddMessages(blockMessages)))
  }


  it should "stop all workers when the blockchain changes" in new WithMiningActor {
    val testMessage = BlockMessage("bla")
    miningActor ! MineBlock(blockChain, Seq(testMessage))
    watch(workerProbe.ref)

    workerProbe.expectMsgPF() {
      case MiningWorker.MineBlock(_, Seq(testMessage), 0, _) => assert(testMessage.data == "bla")
    }

    miningActor ! BlockChainChanged(BlockChain().addMessage("bla").addMessage("die").addMessage("bla"))

    expectTerminated(workerProbe.ref)
  }

}
