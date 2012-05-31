package edu.ucla.sspace

import scala.io.Source


object FilterCorpus {
    def main(args:Array[String]) {
        val validTokens = Source.fromFile(args(0)).getLines.toSet
        def accept(token:String) = validTokens.contains(token)
        for (line <- Source.fromFile(args(1)).getLines) {
            val Array(labels, text) = line.split("\t")
            val filteredText = text.split("\\s+").filter(accept)
            if (filteredText.size > 0)
                printf("%s\t%s\n", labels, filteredText.mkString(" "))
        }
    }
}
