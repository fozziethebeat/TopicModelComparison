package edu.ucla.sspace

import edu.ucla.sspace.basis.StringBasisMapping
import edu.ucla.sspace.similarity.CosineSimilarity
import edu.ucla.sspace.vector.CompactSparseVector
import edu.ucla.sspace.vector.VectorIO

import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.io.Source

import java.io.FileReader
import java.io.PrintWriter


object DisambiguateCorpus {
    def main(args: Array[String]) {
        val writer = new PrintWriter(args(1))
        val basis = new StringBasisMapping()
        Source.fromFile(args(2)).getLines.foreach(w=>basis.getDimension(w))
        basis.setReadOnly(true)
        val window = args(3).toInt
        val simFunc = new CosineSimilarity()

        val senseMap = args.view(4, args.length).map(name => {
            val parts1 = name.split("\\.")
            val chunk = parts1(parts1.length-5).split("-")
            val word = chunk(chunk.length-1)
            val vectors = VectorIO.readSparseVectors(new FileReader(name))
            (word, vectors)
        }).toList.toMap

        val corpus = Source.fromFile(args(0)).getLines
        corpus.foreach(doc => {
            var prev = List[String]()
            var next = List[String]()
            var rewrittenTokens = List[String]()
            val tokenIter = doc.split("\\s+").iterator

            while (next.size < window && tokenIter.hasNext)
                next = next :+ tokenIter.next

            while (!next.isEmpty) {
                val focus = next.head
                next = next.tail
                if (tokenIter.hasNext)
                    next = next :+ tokenIter.next

                val newToken = senseMap.get(focus) match {
                    case Some(senseList) => val contextVector = new CompactSparseVector(basis.numDimensions)
                                            prev.map(basis.getDimension).filter(_>=0).foreach(contextVector.add(_, 1d))
                                            next.map(basis.getDimension).filter(_>=0).foreach(contextVector.add(_, 1d))
                                            val bestIndex = senseList.map(simFunc.sim(_, contextVector))
                                                                     .zipWithIndex
                                                                     .toList
                                                                     .sorted
                                                                     .last._2
                                            focus + "|" + bestIndex
                    case None => focus
                }
                rewrittenTokens = rewrittenTokens :+ newToken

                prev = prev :+ focus
                if (prev.size > window)
                    prev = prev.tail
            }
            writer.println(rewrittenTokens.mkString(" "))
        })
        writer.close
    }
}
