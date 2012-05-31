/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.ucla.sspace

import edu.ucla.sspace.basis.BasisMapping
import edu.ucla.sspace.basis.StringBasisMapping
import edu.ucla.sspace.matrix.Matrix
import edu.ucla.sspace.matrix.MatrixIO
import edu.ucla.sspace.matrix.MatrixIO.Format

import org.apache.avro.ipc.SaslSocketServer
import org.apache.avro.ipc.specific.SpecificResponder

import scala.collection.JavaConversions.asScalaBuffer
import scala.io.Source
import scala.math.log
import java.net.InetSocketAddress


/**
 * Starts the Coherence server.
 */
class CoherenceServer(val pmi: Matrix,
                      val basis:BasisMapping[String,String]) extends Coherence {

    def score(word1: CharSequence, word2: CharSequence, epsilon: Double) = {
        val index1 = basis.getDimension(word1.toString);
        val index2 = basis.getDimension(word2.toString);
        val s = if (index1 < 0 || index2 < 0) 0 else pmi.get(index1, index2)
        log(s + epsilon);
    }

    def coherence(sense: Sense, epsilon: Double) = {
        val words = sense.words
        var total = 0d
        for (List(word1, word2) <- sense.words.toList.combinations(2))
            total += score(word1, word2, epsilon)

        total / (words.size * (words.size - 1) / 2);
    }

    def overlap(sense1: Sense, sense2: Sense, epsilon: Double) = {
        val words1 = sense1.words
        val words2 = sense2.words
        var total = 0d
        for (word1 <- sense1.words; word2 <- sense2.words)
            total += score(word1, word2, epsilon)
        total / (words1.size * words2.size);
    }
}

object CoherenceServer {

    def startServer(pmi: Matrix, 
                    basis: BasisMapping[String, String],
                    port: Int) =
        new SaslSocketServer(new SpecificResponder( 
            classOf[Coherence], new CoherenceServer(pmi, basis)), 
            new InetSocketAddress(port))

    def main(args: Array[String]) {
        if (args.length != 3) {
            println("Usage: <pmiMatrix> <termList> <port>")
            System.exit(1)
        }

        println("Loading the pmi matrix")
        // Load the pmi matrix.
        val pmiMatrix = MatrixIO.readMatrix(args(0), Format.SVDLIBC_SPARSE_TEXT)

        println("Loading the basis mapping")
        // Pre-load the words that describe each dimension.
        val basis = new StringBasisMapping();
        Source.fromFile(args(1)).getLines.foreach(basis.getDimension)
        basis.setReadOnly(true)

        val port = args(2).toInt

        println("Starting the coherence server")
        val server = startServer(pmiMatrix, basis, port)

        server.start()
        printf("Server started on port %d\n", server.getPort);
        server.join()

        server.close()
    }
}
