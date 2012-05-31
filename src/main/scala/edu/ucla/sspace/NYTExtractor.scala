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

import RichFile._

import opennlp.tools.tokenize._

import scala.io.Source
import scala.xml.XML

import java.io.File
import java.io.FileInputStream


object NYTExtractor {

    def main(args: Array[String]) {
        val tokenizerModel = new TokenizerModel(new FileInputStream(args(0)))
        val tokenizer = new TokenizerME(tokenizerModel)
        val stopWords = Source.fromFile(args(1)).getLines.toSet
        val numregex = "[0-9,.-]+"
        def acceptToken(token: String) = !stopWords.contains(token) && !token.matches(numregex)

        val root = new File(args(2))
        for (file <- root.andTree; if file.getName.endsWith(".xml")) {
            val doc = XML.loadFile(file)
            val sections = (doc \\ "meta").filter(node => (node \ "@name").text == "online_sections")
                                          .map(node => (node \ "@content").text)
                                          .mkString(";")
            val text = (doc \\ "block").filter(node => (node \ "@class").text == "full_text")
                                       .flatMap(node => (node \ "p").map(_.text.replace("\n", " ").trim.toLowerCase))
                                       .mkString(" ")
            val tokenizedText = tokenizer.tokenize(text).filter(acceptToken).mkString(" ")
            if (sections.size > 0 && tokenizedText.size > 0)
                printf("%s\t%s\n", sections, tokenizedText)
        }
    }
}
