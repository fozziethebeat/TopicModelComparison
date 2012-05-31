package edu.ucla.sspace

import edu.ucla.sspace.basis.StringBasisMapping
import edu.ucla.sspace.matrix.MatrixIO
import edu.ucla.sspace.matrix.MatrixIO.Format
import edu.ucla.sspace.matrix.SymmetricMatrix
import edu.ucla.sspace.matrix.Matrix

import scala.io.Source

import java.io.File


object ExtractUMassStats {

    def main(args: Array[String]) {
        val EMPTY = ""

        // Create a basis mapping so that each word gets a unique index, and also give
        // the empty string a unique index so that we can easily represent the word
        // occurring with other words in a context that are not tracked.  After loading 
        // each word, set the basis mapping to read only so that we only record counts
        // for the known list of words.
        val basis = new StringBasisMapping()
        Source.fromFile(args(0))
              .getLines
              .map(_.trim)
              .foreach(basis.getDimension(_))
        basis.setReadOnly(true)

        // Now create an array to store co-occurrence counts for each word we care
        // about.
        val wocCounts:Matrix = new SymmetricMatrix(basis.numDimensions, basis.numDimensions)

        // Now iterate through each wikipedia corpus and exctract each sliding window
        // and create a co-occurrence count for each word pair within each window
        var totalCounts = 0
        for ( document <- Source.fromFile(args(2), "ISO-8859-1").getLines ) {
            val Array(label, text) = document.split("\\t", 2)
            val wordIds = text.split("\\s+")
                              .toSet
                              .map(basis.getDimension)
                              .filter(_>=0) 
            // Add a count for each word occurring in a document.
            for (wordId <- wordIds)
                wocCounts.add(wordId, wordId, 1)
            // Compute the combinations of each word pair and increment the counts.
            for (List(w1, w2) <- wordIds.toList.combinations(2))
                wocCounts.add(w1, w2, 1)
        }

        for (r <- 0 until wocCounts.rows;
             c <- r until wocCounts.columns ) {
            val denom = wocCounts.get(c, c)
            val joint = wocCounts.get(r, c) / denom
            wocCounts.set(r, c, joint)
        }

        MatrixIO.writeMatrix(wocCounts, new File(args(1)), Format.SVDLIBC_SPARSE_TEXT)
    }
}
