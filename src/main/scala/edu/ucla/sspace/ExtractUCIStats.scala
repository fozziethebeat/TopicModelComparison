package edu.ucla.sspace

import edu.ucla.sspace.basis.StringBasisMapping
import edu.ucla.sspace.matrix.MatrixIO
import edu.ucla.sspace.matrix.MatrixIO.Format
import edu.ucla.sspace.matrix.SymmetricMatrix
import edu.ucla.sspace.matrix.Matrix

import scala.io.Source

import java.io.File

object ExtractUCIStats {
    def main(args: Array[String]) {
        val EMPTY = ""

        // Create a basis mapping so that each word gets a unique index, and also give the empty string a unique index so that we can easily
        // represent the word occurring with other words in a context that are not tracked.  After loading // each word, set the basis
        // mapping to read only so that we only record counts for the known list of words.
        val basis = new StringBasisMapping()
        Source.fromFile(args(0)).getLines.map(_.trim).foreach(basis.getDimension(_))
        basis.setReadOnly(true)

        // Now create an array to store co-occurrence counts for each word we care
        // about.
        val pmiCounts:Matrix = new SymmetricMatrix(basis.numDimensions, basis.numDimensions)

        // Now iterate through each wikipedia corpus and exctract each sliding window
        // and create a co-occurrence count for each word pair within each window
        var totalCounts = 0
        for ( document <- Source.fromFile(args(2), "ISO-8859-1").getLines ) {
            val Array(label, text) = document.split("\\t", 2)
            val tokens = text.split("\\s+").toList

            for ( window <- tokens.sliding(20);
                  List(word1, word2) <- window.toSet.toList.combinations(2) ) {
                (basis.getDimension(word1), basis.getDimension(word2)) match {
                    case (-1, -1) => 
                    case (-1, j) => pmiCounts.add(j, j, 1)
                    case (i, -1) => pmiCounts.add(i, i, 1)
                    case (i,j) => pmiCounts.add(i,j,1)
                                  pmiCounts.add(j, j, 1)
                                  pmiCounts.add(i, i, 1)
                }
                totalCounts += 1
            }
        }

        for (r <- 0 until pmiCounts.rows;
             c <- r until pmiCounts.columns ) {
            val denom = pmiCounts.get(r,r) * pmiCounts.get(c,c)
            val pmi = if (denom == 0) 0 else pmiCounts.get(r,c) * totalCounts / denom
            pmiCounts.set(r,c, pmi)
        }

        MatrixIO.writeMatrix(pmiCounts, new File(args(1)), Format.SVDLIBC_SPARSE_TEXT)
    }
}
