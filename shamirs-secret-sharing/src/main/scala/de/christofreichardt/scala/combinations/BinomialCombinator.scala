/*
 * Shamirs Keystore
 *
 * Copyright (C) 2017, 2022, Christof Reichardt
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

package de.christofreichardt.scala.combinations

import de.christofreichardt.diagnosis.{AbstractTracer, TracerFactory}
import de.christofreichardt.scala.diagnosis.Tracing
import scala.collection.mutable.ListBuffer

/**
 * Produces all combinations of k unordered items which can be chosen from among n items (k <= n). If k == n every item is to be selected. On the other hand
 * if k == 0 none item will be selected. In the both cases there is exactly one solution: all items or the the empty set. If k > 0 and k < n we have
 * two choices: Either a particular item will be selected or discarded. In the former case k decreases and in the latter case k remains unchanged.
 * Now we have a new problem instance which can be solved recursively.
 *
 * @constructor Creates a particular problem instance.
 *
 * @param items the basic set of items
 * @param k the number of to be selected items
 * @tparam T the type of the provided items
 */
class BinomialCombinator[T](items: IndexedSeq[T], val k: Int) extends Tracing {
  require(items.size >= k, "The number of to be selected items must be less than the total number of items.")

  val combination: Combination[T] = new Combination(IndexedSeq.tabulate(items.size)(index => new Element(items(index), State.NEITHER)), k)

  lazy val solutions: List[IndexedSeq[T]] = {
    produce
      .toList
      .map(seq => seq.filter(element => element.state == State.SELECTED))
      .map(seq => seq.map(element => element.item))
  }

  def produce: ListBuffer[IndexedSeq[Element[T]]] = {

    def combinate(solutions: ListBuffer[IndexedSeq[Element[T]]], current: Combination[T]): Unit = {
      withTracer("Unit", this, "combinate(solutions: ListBuffer[IndexedSeq[Element[T]]])") {
        val tracer = getCurrentTracer()
        tracer.out().printfIndentln("current = %s", current)
        if (current.k == 0) solutions.append(current.elements)
        else {
          combinate(solutions, current.selectFirst())
          // Obviously we are allowed to discard elements if and only if the number of still to be selected elements (== k) is less than the remaining elements
          if (current.k < current.remaining) combinate(solutions, current.discardFirst())
        }
      }
    }

    withTracer("ListBuffer[IndexedSeq[Element[T]]]", this, "produce()") {
      val solutions = ListBuffer.empty[IndexedSeq[Element[T]]]
      combinate(solutions, this.combination)
      solutions
    }
  }

  override def toString: String = String.format("BinomialCombinator[combination=%s]", this.combination)

//  override def getCurrentTracer(): AbstractTracer = {
//    try {
//      TracerFactory.getInstance().getTracer("TestTracer")
//    }
//    catch {
//      case ex: TracerFactory.Exception => TracerFactory.getInstance().getDefaultTracer
//    }
//  }
}
