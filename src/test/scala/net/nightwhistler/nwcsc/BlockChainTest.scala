package net.nightwhistler.nwcsc

import net.nightwhistler.nwcsc.blockchain._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.FlatSpec
import org.scalatest.prop.GeneratorDrivenPropertyChecks

import scala.collection.immutable.Seq

class BlockChainTest extends FlatSpec with GeneratorDrivenPropertyChecks {

  implicit val arbitraryChain: Arbitrary[BlockChain] = Arbitrary {
    for {
      length <- Gen.choose(0, 30)
      text <- Gen.listOfN(length, Gen.alphaNumStr)
    } yield {
      val chain = BlockChain(NoDifficulty, SimpleSHA256Hash)
      text.foreach { data => chain.addMessage(data) }
      chain
    }
  }

  "Any generated blockchain that is succesful " must "also be valid" in forAll { chain: BlockChain =>
    assertResult(true)(chain.valid)
  }

  "For any given chain, the first block" must "be the Genesis block" in forAll { chain: BlockChain =>
    assertResult(GenesisBlock)(chain.firstBlock)
  }

  "Adding an invalid block" should "never work" in forAll { (chain: BlockChain, firstName: String, secondName: String) =>

    val firstNewBlock = chain.generateNextBlock(Seq(BlockMessage(firstName)), "", 0)
    val secondNewBlock = chain.generateNextBlock(Seq(BlockMessage(secondName)), "", 0)

    val currentBlockLength = chain.blocks.length

    val newChain = chain.addBlock(firstNewBlock).getOrElse( throw new IllegalStateException("Should succeed"))

    assert( ! newChain.validBlock(secondNewBlock) )
    newChain.addBlock(secondNewBlock)

    assertResult(newChain.blocks.length)(currentBlockLength +1)
    assertResult(newChain.head)(firstNewBlock)
  }
}
