package codes.quine.labo.redos
package automaton

import scala.collection.mutable

import data.Graph
import data.MultiSet
import util.GraphvizUtil.escape
import util.Timeout

/** NFAwLA is a NFA with a look-ahead DFA.
  *
  * This NFA uses a multi-set instead of a set for duplicated transitions.
  * A look-ahead DFA is a DFA which is constructed from the reversed original NFA.
  * A state of this NFA is a pair of the original NFA state and the look-ahead DFA state,
  * and the transition function must be pruned along with the backtrack behavior.
  */
final case class NFAwLA[A, Q](
    alphabet: Set[(A, Set[Q])],
    stateSet: Set[(Q, Set[Q])],
    initSet: MultiSet[(Q, Set[Q])],
    acceptSet: Set[(Q, Set[Q])],
    delta: Map[((Q, Set[Q]), (A, Set[Q])), MultiSet[(Q, Set[Q])]],
    lookAheadDFA: DFA[A, Set[Q]]
) {

  /** Exports this transition function as a graph. */
  def toGraph(implicit timeout: Timeout = Timeout.NoTimeout): Graph[(Q, Set[Q]), (A, Set[Q])] =
    timeout.checkTimeout("automaton.MultiNFA#toGraph") {
      Graph.from(delta.iterator.flatMap { case (q1, a) -> qs =>
        qs.iterator.map((q1, a, _))
      }.toIndexedSeq)
    }

  /** Converts to Graphviz format text. */
  def toGraphviz: String = {
    val sb = new mutable.StringBuilder

    def showQ(qps: (Q, Set[Q])): String = {
      val (q, ps) = qps
      escape(s"($q, ${ps.map(_.toString).toSeq.sorted.mkString("{", ",", "}")})")
    }

    sb.append("digraph {\n")
    sb.append(s"  ${escape("")} [shape=point];\n")
    for (init <- initSet) sb.append(s"  ${escape("")} -> ${showQ(init)};\n")
    for (q <- stateSet) sb.append(s"  ${showQ(q)} [shape=${if (acceptSet.contains(q)) "double" else ""}circle];\n")
    for (((q0, a), qs) <- delta; q1 <- qs)
      sb.append(s"  ${showQ(q0)} -> ${showQ(q1)} [label=${escape(a._1)}];\n")
    sb.append("}")

    sb.result()
  }
}