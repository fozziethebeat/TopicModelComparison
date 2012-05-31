package edu.ucla.sspace

import SenseUtil.{readSenses,newSense}

import org.apache.avro.ipc.SaslSocketTransceiver
import org.apache.avro.ipc.specific.SpecificRequestor

import java.net.InetSocketAddress


/**
 * @author Keith Stevens
 */
object SenseCoherence {

    def main(args: Array[String]) {
        val client = new SaslSocketTransceiver(new InetSocketAddress(args(0).toInt))
        val proxy = SpecificRequestor.getClient(
                classOf[Coherence], client)

        val epsilon = args(2).toDouble
        for ((senseWords, i) <- readSenses(args(1)).zipWithIndex) {
            printf("%s %d %f\n", args(3), i,
                                 proxy.coherence(newSense(senseWords), epsilon))
        }
        client.close()
    }
}
