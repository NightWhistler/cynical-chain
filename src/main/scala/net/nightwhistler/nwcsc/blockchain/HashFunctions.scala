package net.nightwhistler.nwcsc.blockchain

import com.roundeights.hasher.Implicits._
import net.nightwhistler.nwcsc.blockchain.BlockChain.HashFunction

object SimpleSHA256Hash extends HashFunction {
  def apply( block: Block ): BigInt = calculateHash(block.index, block.previousHash, block.timestamp,
    block.foundBy, block.messages, block.nonce)

  private def calculateHash(index: Long, previousHash: BigInt, timestamp: Long, foundBy: String,
                            messages: Seq[BlockMessage], nonce: Long) =
    BigInt(1, s"$index:${previousHash.toString(16)}:$timestamp:$foundBy:${contentsAsString(messages)}:$nonce".sha256.bytes)

  private def contentsAsString( messages: Seq[BlockMessage]) =
    messages.map{ case BlockMessage(data, id) => s"${data}:${id}" }.mkString(":")
}
