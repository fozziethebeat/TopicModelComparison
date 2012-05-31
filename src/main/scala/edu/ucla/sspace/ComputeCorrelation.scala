package edu.ucla.sspace

import edu.ucla.sspace.matrix.MatrixIO
import edu.ucla.sspace.matrix.MatrixIO.Format
import edu.ucla.sspace.similarity.CosineSimilarity
import edu.ucla.sspace.similarity.SpearmanRankCorrelation
import edu.ucla.sspace.vector.DenseVector

import scala.io.Source


object ComputeCorrelation {
    def main(args:Array[String]) {
        val wordMap = Source.fromFile(args(0)).getLines.zipWithIndex.toMap
        val wordSpace = MatrixIO.readMatrix(args(1), Format.DENSE_TEXT)
        val pairLines = Source.fromFile(args(2)).getLines
        pairLines.next
        val wordPairs = pairLines.toList.filter(!_.startsWith("#")).map(line => {
            val Array(w1, w2, s) = line.split("\\s+")
            (w1, w2, s.toDouble)
        })

        val humanScores = new DenseVector(wordPairs.size)
        val modelScores = new DenseVector(wordPairs.size)
        val metric = new CosineSimilarity()
        for ( ((w1, w2, humanScore), i) <- wordPairs.zipWithIndex) {
            val modelScore = metric.sim(wordSpace.getRowVector(wordMap(w1)),
                                        wordSpace.getRowVector(wordMap(w2)))
            humanScores.set(i, humanScore)
            modelScores.set(i, modelScore)
        }
        val correlation = new SpearmanRankCorrelation()
        printf("%s %f\n", args(3), correlation.sim(humanScores, modelScores))
    }
}
