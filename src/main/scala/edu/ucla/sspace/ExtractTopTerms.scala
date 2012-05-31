package edu.ucla.sspace

import edu.ucla.sspace.matrix.Matrix.Type
import edu.ucla.sspace.matrix.MatrixIO
import edu.ucla.sspace.matrix.MatrixIO.Format

import scala.io.Source

import java.io.File


object ExtractTopTerms {
    def main(args: Array[String]) {
        val keyWords = Source.fromFile(args(0)).getLines.toArray
        val topicSpace = MatrixIO.readMatrix(new File(args(2)), Format.DENSE_TEXT, Type.DENSE_IN_MEMORY, true)
        val numWords = args(1).toInt
        for (topic <- 0 until topicSpace.rows) {
            val bestWords = (0 until topicSpace.columns).map(word => (topicSpace.get(topic, word), word))
                                                        .sorted
                                                        .reverse
                                                        .take(numWords)
                                                        .map(_._2)
                                                        .map(keyWords)
              println(bestWords.mkString(" "))
        }
    }
}
