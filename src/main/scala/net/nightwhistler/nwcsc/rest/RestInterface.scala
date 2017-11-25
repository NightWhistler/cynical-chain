package net.nightwhistler.nwcsc.rest

import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import net.nightwhistler.nwcsc.actor.BlockChainActor._
import net.nightwhistler.nwcsc.actor.PeerToPeer.{AddPeer, GetPeers, Peers}
import net.nightwhistler.nwcsc.blockchain.{Block, BlockMessage, GenesisBlock}
import org.json4s.JsonAST.JString
import org.json4s.{CustomSerializer, DefaultFormats, Serializer, native}

import scala.concurrent.{ExecutionContext, Future}

class BigIntHexSerializer extends CustomSerializer[BigInt](format => ( {
  case JString(s) => BigInt(s, 16)
}, {
  case i: BigInt => JString(i.toString(16))
}
))

trait RestInterface extends Json4sSupport {

  val peerToPeerActor: ActorRef

  val logger: Logger

  implicit val serialization = native.Serialization
  implicit val stringUnmarshallers = PredefinedFromEntityUnmarshallers.stringUnmarshaller

  implicit object Format extends DefaultFormats {
    override val customSerializers: List[Serializer[_]] = List(new BigIntHexSerializer)
  }

  implicit val executionContext: ExecutionContext

  implicit val timeout = Timeout(5, TimeUnit.SECONDS)

  val routes =
    get {
      path("blocks") {
        val chain: Future[Seq[Block]] = (peerToPeerActor ? GetBlockChain).map {
          //This is a bit of a hack, since JSON4S doesn't serialize case objects well
          case CurrentBlockChain(blockChain) => blockChain.blocks.slice(0, blockChain.blocks.length -1) :+ GenesisBlock.copy()
        }
        complete(chain)
      }~
      path("peers") {
        complete( (peerToPeerActor ? GetPeers).mapTo[Peers] )
      }~
      path("latestBlock") {
        complete( (peerToPeerActor ? QueryLatest).map {
          case NewBlock(GenesisBlock) => GenesisBlock.copy()
          case NewBlock(block) => block
        })
      }
    }~
    post {
     path("mineBlock") {
       entity(as[String]) { data =>
         logger.info(s"Got request to add new block $data")
         val blockMessage = BlockMessage(data)
         peerToPeerActor ! AddMessages(Seq(blockMessage))
         complete(blockMessage)
      }
    }~
    path("addPeer") {
      entity(as[String]) { peerAddress =>
        logger.info(s"Got request to add new peer $peerAddress")
        peerToPeerActor ! AddPeer(peerAddress)
        complete(s"Added peer $peerAddress")
      }
    }
  }
}
