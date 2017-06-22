package net.nightwhistler.nwcsc

import net.nightwhistler.nwcsc.blockchain.BlockChain.DifficultyFunction
import net.nightwhistler.nwcsc.blockchain._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.FlatSpec
import org.scalatest.prop.GeneratorDrivenPropertyChecks

object DummyDifficultyFunction extends DifficultyFunction {
  override def apply(b: Block): BigInt = NaiveCoinDifficulty.BASE_DIFFICULTY
}

/**
  * Created by alex on 13-6-17.
  */
class BlockChainTest extends FlatSpec with GeneratorDrivenPropertyChecks {

  implicit val arbitraryChain: Arbitrary[BlockChain] = Arbitrary {
    for {
      length <- Gen.choose(0, 30)
      text <- Gen.listOfN(length, Gen.alphaNumStr)
    } yield {
      val chain = BlockChain(DummyDifficultyFunction, SimpleSHA256Hash)
      text.foreach { data => chain.addBlock(chain.generateNextBlock(Seq(BlockMessage(data)))) }
      chain
    }
  }

  "Generated chains" should "always be correct" in forAll { chain: BlockChain =>
    assert( chain.validChain( chain.blocks ) )
  }

  "For any given chain, the first block" must "be the Genesis block" in forAll { chain: BlockChain =>
    assertResult(GenesisBlock)(chain.firstBlock)
  }

  "Adding an invalid block" should "never work" in forAll { (chain: BlockChain, firstName: String, secondName: String) =>

    val firstNewBlock = chain.generateNextBlock(Seq(BlockMessage(firstName)))
    val secondNewBlock = chain.generateNextBlock(Seq(BlockMessage(secondName)))

    val currentBlockLength = chain.blocks.length

    val newChain = chain.addBlock(firstNewBlock).getOrElse( throw new IllegalStateException("Should succeed"))

    assert( ! newChain.validBlock(secondNewBlock) )
    newChain.addBlock(secondNewBlock)

    assertResult(newChain.blocks.length)(currentBlockLength +1)
    assertResult(newChain.latestBlock)(firstNewBlock)
  }
}
