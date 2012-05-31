package edu.ucla.sspace

import scala.io.Source
import scala.util.Random


object FormFolds {
    def main(args:Array[String]) {
        val numFolds = args(1).toInt
        val minOccurances = args(2).toInt
        val folds = Array.fill(numFolds)(Array[Int]().toBuffer)
        val labelToDataMap = Source.fromFile(args(0))
                                   .getLines
                                   .map(_.split(";"))
                                   .zipWithIndex
                                   .filter(_._1.size == 1)
                                   .map(x => (x._1(0), x._2))
                                   .toList
                                   .groupBy(_._1)
                                   .filter( kv => kv._2.size > minOccurances)
                                   .values
                                   .map(_.map(_._2))
                                   .foreach( ids => 
                                       for ( (v, f) <- Random.shuffle(ids).zipWithIndex )
                                           folds(f%numFolds).append(v)
                                   )
        folds.foreach(f => println(Random.shuffle(f).mkString(" ")))
    }
}
