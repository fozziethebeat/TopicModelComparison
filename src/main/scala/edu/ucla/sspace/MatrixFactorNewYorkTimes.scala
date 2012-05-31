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

import edu.ucla.sspace.matrix.Matrices;
import edu.ucla.sspace.matrix.MatrixFile;
import edu.ucla.sspace.matrix.MatrixIO;
import edu.ucla.sspace.matrix.MatrixIO.Format;
import edu.ucla.sspace.matrix.factorization.NonNegativeMatrixFactorizationMultiplicative
import edu.ucla.sspace.matrix.factorization.SingularValueDecompositionLibC


/**
 * Reduces a matrix using either NMF or SVD.
 */
object MatrixFactorNewYorkTimes {
    def main(args:Array[String]) {
        val reduction = args(1) match {
            case "nmf" => new NonNegativeMatrixFactorizationMultiplicative();
            case "svd" => new SingularValueDecompositionLibC();
        }
        val numDim = args(2).toInt

        val matrixFile = new MatrixFile(args(0), Format.SVDLIBC_SPARSE_TEXT)
        reduction.factorize(matrixFile, numDim)

        MatrixIO.writeMatrix(reduction.dataClasses, args(3), Format.DENSE_TEXT)
        MatrixIO.writeMatrix(Matrices.transpose(reduction.classFeatures), args(4), Format.DENSE_TEXT)
    }
}
