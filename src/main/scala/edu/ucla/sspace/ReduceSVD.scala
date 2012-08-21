package edu.ucla.sspace

import scala.io.Source

import java.io.PrintWriter


object ReduceSVD {
    def main(args: Array[String]) {
        val writer = new PrintWriter(args(2))
        val numTopics = args(0).toInt
        for (line <- Source.fromFile(args(1)).getLines)
            writer.println(line.split("\\s+").view(0, numTopics).mkString(" "))
        writer.close
    }
}
