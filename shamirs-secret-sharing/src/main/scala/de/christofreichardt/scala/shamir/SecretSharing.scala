/*
 * Shamirs Keystore
 *
 * Copyright (C) 2017, 2020, Christof Reichardt
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.christofreichardt.scala.shamir

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.SecureRandom
import java.util.UUID

import de.christofreichardt.scala.combinations.BinomialCombinator
import de.christofreichardt.scala.diagnosis.Tracing
import de.christofreichardt.scala.utils.{JsonPrettyPrinter, RandomGenerator}
import javax.json.{Json, JsonArray, JsonObject}

import scala.annotation.tailrec

class SecretSharing(
                     val shares: Int,
                     val threshold: Int,
                     val secretBytes: IndexedSeq[Byte],
                     val random: SecureRandom)
  extends Tracing {

  def this(secretBytes: IndexedSeq[Byte]) = this(6, 3, secretBytes, new SecureRandom)

  def this(shares: Int, threshold: Int, secretBytes: IndexedSeq[Byte]) = this(shares, threshold, secretBytes, new SecureRandom)

  def this(shares: Int, threshold: Int, secretBytes: Array[Byte]) = this(shares, threshold, secretBytes.toIndexedSeq, new SecureRandom)

  def this(shares: Int, threshold: Int, password: String) = this(shares, threshold, password.getBytes(StandardCharsets.UTF_8).toIndexedSeq, new SecureRandom)

  def this(shares: Int, threshold: Int, password: CharSequence) = this(shares, threshold, charSequenceToByteArray(password))

  val n: Int = shares
  val k: Int = threshold
  val s: BigInt = bytes2BigInt(secretBytes)
  val randomGenerator: RandomGenerator = new RandomGenerator(random)
  val prime: BigInt = choosePrime
  val polynomial: Polynomial = choosePolynomial(k - 1)
  val sharePoints: IndexedSeq[(BigInt, BigInt)] = computeShares
  val id: String = UUID.randomUUID().toString
  lazy val sharePointsAsJson: JsonObject = sharePointsAsJson(sharePoints)
  lazy val verified: Boolean = verifyAll

  require(n >= 2 && k >= 2, "We need at least two shares, otherwise we wouldn't need shares at all.")
  require(k <= n, "The threshold must be less than or equal to the number of shares.")
  require(polynomial.degree == k - 1)

  def choosePrime: BigInt = {
    val BIT_OFFSET = 1
    val bits = s.bitLength + BIT_OFFSET
    BigInt(bits, CERTAINTY, random)
  }

  def chooseCanonicalCoefficients: IndexedSeq[BigInt] = {
    val bits = s.bitLength * 2
    randomGenerator.bigIntStream(bits, prime).take(k - 1).toIndexedSeq
  }

  @tailrec
  final def choosePolynomial(degree: Int): Polynomial = {
    val candidate: Polynomial = new Polynomial(chooseCanonicalCoefficients :+ s, prime)
    if (candidate.degree == degree) candidate
    else choosePolynomial(degree)
  }

  def computeShares: IndexedSeq[(BigInt, BigInt)] = {
    val bits = s.bitLength * 2
    randomGenerator.bigIntStream(bits, prime)
      .filterNot(x => x == BigInt(0))
      .take(shares)
      .map(x => (x, polynomial.evaluateAt(x)))
      .toIndexedSeq
  }

  def verifyAll: Boolean = {
    val combinator = new BinomialCombinator[Int](IndexedSeq.range(0, n), k)
    combinator.solutions
      .map(combination => {
        val indices = combination
        val selectedPoints = indices.map(index => sharePoints(index))
        val merger = SecretMerging(selectedPoints, prime)
        merger.secretBytes
      })
      .forall(bytes => bytes == secretBytes)
  }

  def sharePointsAsJson(ps: IndexedSeq[(BigInt, BigInt)]): JsonObject = {
    val arrayBuilder = Json.createArrayBuilder()
    ps.foreach(ps => {
      arrayBuilder.add(Json.createObjectBuilder()
        .add("SharePoint", Json.createObjectBuilder()
          .add("x", ps._1.bigInteger)
          .add("y", ps._2.bigInteger)))
    })
    Json.createObjectBuilder()
      .add("Id", id)
      .add("Prime", prime.bigInteger)
      .add("Threshold", threshold)
      .add("SharePoints", arrayBuilder.build())
      .build
  }

  /**
    * Partitions the share points into disjunct sequences according to the given sizes.
    *
    * @param sizes denotes the sizes of the desired share point sequences
    * @return a list of share point sequences
    */
  def sharePointPartition(sizes: Iterable[Int]): List[IndexedSeq[(BigInt, BigInt)]] = {
    require(sizes.sum == sharePoints.length, "The sum of the shares of each slice doesn't match the number of overall shares.")
    require(sizes.forall(s => s <= k), "A partition must not exceed the threshold.")

    @tailrec
    def partition(sizes: Iterable[Int], remainingPoints: IndexedSeq[(BigInt, BigInt)], partitions: List[IndexedSeq[(BigInt, BigInt)]]): List[IndexedSeq[(BigInt, BigInt)]] = {
      if (sizes.isEmpty) partitions
      else partition(sizes.tail, remainingPoints.drop(sizes.head), remainingPoints.take(sizes.head) :: partitions)
    }

    partition(sizes, sharePoints, List())
  }

  def partitionAsJson(sizes: Array[Int]): JsonArray = {
    val partition = sharePointPartition(sizes)
    val arrayBuilder = Json.createArrayBuilder()
    partition.map(slice => sharePointsAsJson(slice))
      .foreach(slice => arrayBuilder.add(slice))
    arrayBuilder.build()
  }

  def savePartition(sizes: Iterable[Int], path: Path): Unit = {
    require(path.getParent.toFile.exists() && path.getParent.toFile.isDirectory)
    val prettyPrinter = new JsonPrettyPrinter
    prettyPrinter.print(path.getParent.resolve(path.getFileName.toString + ".json").toFile, sharePointsAsJson)
    val partition = sharePointPartition(sizes)
    partition.map(part => sharePointsAsJson(part))
      .zipWithIndex
      .foreach({
        case (jsonObject, i) => prettyPrinter.print(path.getParent.resolve(path.getFileName.toString + "-" + i + ".json").toFile, jsonObject)
      })
  }

  def savePartition(sizes: Array[Int], path: Path): Unit = savePartition(sizes.reverse.toIterable, path)

  override def toString: String = String.format("SecretSharing[shares=%d, threshold=%d, s=%s, polynomial=%s, sharePoints=(%s)]", shares: Integer, threshold: Integer, s, polynomial, sharePoints.mkString(","))
}