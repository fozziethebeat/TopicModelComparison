package edu.ucla.sspace
import scala.io.Source

object ReduceSVD {
    def main(args: Array[String]) {
        val numTopics = args(0).toInt
        for (line <- Source.fromFile(args(1)).getLines)
            println(line.split("\\s+").view(0, numTopics).mkString(" "))
    }
}
