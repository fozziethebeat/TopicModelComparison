package edu.ucla.sspace

import edu.ucla.sspace.basis.StringBasisMapping
import edu.ucla.sspace.matrix.GrowingSparseMatrix
import edu.ucla.sspace.matrix.LogEntropyTransform
import edu.ucla.sspace.matrix.MatrixIO
import edu.ucla.sspace.matrix.MatrixIO.Format
import edu.ucla.sspace.matrix.NoTransform
import edu.ucla.sspace.matrix.TfIdfTransform

import scala.io.Source


object BuildTermDocMatrix {
    def main(args: Array[String]) {
        val basis = new StringBasisMapping()
        Source.fromFile(args(0)).getLines.foreach(w=>basis.getDimension(w))
        basis.setReadOnly(true)

        val transform = args(1) match {
            case "tfidf" =>  new TfIdfTransform()
            case "logentropy" => new LogEntropyTransform()
            case "none" => new NoTransform()
        }

        val termDocMatrix = new GrowingSparseMatrix(basis.numDimensions, 1)
        for ((doc, docId) <- Source.fromFile(args(2)).getLines.zipWithIndex) {
            val Array(_, text) = doc.split("\t")
            for (wordId <- text.split("\\s+").map(w=>basis.getDimension(w)))
                termDocMatrix.add(wordId, docId, 1d)
        }
        transform.transform(termDocMatrix)

        MatrixIO.writeMatrix(termDocMatrix, args(3), Format.SVDLIBC_SPARSE_TEXT)
    }
}
