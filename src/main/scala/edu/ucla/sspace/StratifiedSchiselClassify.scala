/*
 * Copyright (c) 2012, Lawrence Livermore National Security, LLC. Produced at
 * the Lawrence Livermore National Laboratory. Written by Keith Stevens,
 * kstevens@cs.ucla.edu OCEC-10-073 All rights reserved. 
 *
 * This file is part of the S-Space package and is covered under the terms and
 * conditions therein.
 *
 * The S-Space package is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation and distributed hereunder to you.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND NO REPRESENTATIONS OR WARRANTIES,
 * EXPRESS OR IMPLIED ARE MADE.  BY WAY OF EXAMPLE, BUT NOT LIMITATION, WE MAKE
 * NO REPRESENTATIONS OR WARRANTIES OF MERCHANT- ABILITY OR FITNESS FOR ANY
 * PARTICULAR PURPOSE OR THAT THE USE OF THE LICENSED SOFTWARE OR DOCUMENTATION
 * WILL NOT INFRINGE ANY THIRD PARTY PATENTS, COPYRIGHTS, TRADEMARKS OR OTHER
 * RIGHTS.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package edu.ucla.sspace

import cc.mallet.classify._
import cc.mallet.types._

import edu.ucla.sspace.common.ArgOptions
import edu.ucla.sspace.matrix.MatrixIO
import edu.ucla.sspace.matrix.MatrixIO.Format
import edu.ucla.sspace.vector.DoubleVector
import edu.ucla.sspace.vector.SparseDoubleVector

import scala.collection.JavaConversions.asScalaBuffer
import scala.io.Source


/**
 * A simple wrapper around <a href="http://mallet.cs.umass.edu/">Mallet's</a>
 * classifier code that simplifies the training and evaluation of a classifier
 * with arbitrary feature spaces.  Mallet's command line tool assumes that the
 * input will be text documents.  This code assumes that the data points are
 * represented as rows in a matrix, where the features can be any arbitrary
 * type.
 * 
 * </p>
 * 
 * This code includes a simple Bagged Decision Tree trainer.
 * 
 * </p>
 *
 * This code requires the following arguments, in the following order:
 * <ol>
 *   <li> trainer: an abreviation of a classifier trainer in mallet.  Can be:
 *        nb,c45,dt,me, or bag. </li>
 *   <li> dataMatrix: a SVDLIBC_SPARSE_TEXT or DENSE_TEXT matrix file where each
 *        row is a data point and each column is a feature.</li>
 *   <li> idFile: a unique idenfier for each row in dataMatrix, in the same
 *        order as the rows, with one identifier per line.</li>
 *   <li> labelFile: the class label associated with each row in dataMatrix,
 *        with one label per row in the same order as in dataMatrix.</li>
 *   <li> testLabels: a file listing the test identifiers used for evaluation.
 *        This should be a subset of the ids in idFile.  Each line should have 
 *        two parts, "anytoken identifier" per line.  The ordering does not 
 *        matter.</li>
 * </ol>
 *
 * </p>
 *
 * This code also supports the following options:
 * <ul>
 *  <li> -d, dense: Set to true if the data matrix is in a dense format, 
 *                  otherwise the data is assumed to be in the 
 *                  SVDLIBC_SPARSE_TEXT format.</li>
 * </ul>
 *
 * </p>
 *
 * Classification results for the test set will be printed in the form:
 * </br>
 * identifier_base identifier label
 * </br>
 */
object SchiselClassify {

    /**
     * Create a Mallet {@link Instance} from a row vector in a matrix.  The row
     * should have sense label {@code sense} and unique identifier {@code id}.
     * {@code alphabet} is required in order to create the feature vector and
     * {@code classes} maintains the set of possible classes labels and will be
     * updated with the {@code sense} label.
     */
    def makeInstance(rowVector:DoubleVector, 
                     label: String, 
                     id: String,
                     alphabet: Alphabet, 
                     classes: LabelAlphabet) = {
        // Check to see whether or not row vector is sparse.  If it is, only
        // extract the non zero values and indices , otherwise evaluate all
        // indices.
        val (nonZeros, values) = rowVector match {
            case sv:SparseDoubleVector => {
                val nz = sv.getNonZeroIndices
                (nz, nz.map( i => sv.get(i)))
            }
            case v:DoubleVector =>
                (0 until v.length toArray, v.toArray)
        }  

        // Create a new feature vector using the given alphabet and classes
        // mapping for sense.
        new Instance(new FeatureVector(alphabet, nonZeros, values),
                     classes.lookupLabel(label), id, null)
    }
        
    def main(vargs:Array[String]) {
        // Parse the command line options.
        val options = new ArgOptions()
        options.addOption('d', "dense", 
                          "Set to true if the data matrix is in a dense " +
                          "format, otherwise the data is assumed to be in the" +
                          "SVDLIBC_SPARSE_TEXT format.",
                          false, null, "Optional")
        val args = options.parseOptions(vargs)
        
        // Load the data matrix to be evaluated.  
        println("Loading data matrix")
        val m = if (options.hasOption('d'))
                    MatrixIO.readMatrix(args(1), Format.DENSE_TEXT)
                else
                    MatrixIO.readSparseMatrix(args(1), Format.SVDLIBC_SPARSE_TEXT, true)

        // Read the class labels associated with each data point, in the same
        // order as the rows for the matrix.
        println("Loading data labels")
        val labels = Source.fromFile(args(2)).getLines.toArray

        // Mallet requires each feature to be represented by some descriptive
        // object, so create a generic object for each feature corresponding to
        // the feature index and turn it into an alphabet.  
        val terms:Array[Object] = (0 until m.columns).map(_.toString).toArray
        val alphabet = new Alphabet(terms)

        // Create an alphabet to record the set of possible class values each
        // object can take.
        val classes = new LabelAlphabet()

        println("Loading data instance folds")
        val instanceFolds = Source.fromFile(args(3))
                                  .getLines
                                  .toList
                                  .map(_.split("\\s+")
                                        .toList
                                        .map(_.toInt)
                                        .map(id => makeInstance(m.getRowVector(id), labels(id), id.toString, alphabet, classes)))


        var correctAvg = 0d
        for ( (fold, f) <- instanceFolds.zipWithIndex) {
            printf("Validating fold [%d]\n", f)
            val instanceList = new InstanceList(alphabet, classes)
            for ((trainFold, tf) <- instanceFolds.zipWithIndex; 
                 if tf != f;
                 instance <- trainFold)
                instanceList.add(instance)
            val trainer = getTrainer(args(0))
            val classifier = trainer.train(instanceList)

            val correct = fold.map(testInstance => {
                val label = classifier.classify(testInstance).getLabeling.getBestLabel
                val gold = testInstance.getLabeling.getBestLabel
                if (label.toString == gold.toString) 1 else 0
            }).sum
            val accuracy = correct / fold.size.toDouble
            printf("Accuracay on fold [%d]: %f\n", f, accuracy)
            correctAvg += accuracy
        }
        printf("%s %f\n", args(4), correctAvg/instanceFolds.size)
    }

    def getTrainer(name: String) = 
        name match {
            case "nb" => new NaiveBayesTrainer()
            case "c45" => new C45Trainer()
            case "dt" => new DecisionTreeTrainer()
            case "me" => new MaxEntTrainer()
        }
}
