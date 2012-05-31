package edu.ucla.sspace;

import scala.collection.JavaConversions.seqAsJavaList
import scala.io.Source


/**
 * @author Keith Stevens
 */
object SenseUtil {

    def readSenses(senseFile: String) =
        Source.fromFile(senseFile)
               .getLines
               .map(_.split("\\s+").toList)
    def newSense(senseWords: List[String]) = {
        val sense = new Sense()
        sense.setWords(senseWords)
        sense
    }
}
