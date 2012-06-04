# Detailed Setup for Exploring Topic Coherence over many models and many topics

We (Keith Stevens, Philip Kegelmeyer, David Andrzejewski, and David Buttler)
published the paper [Exploring Topic Coherence over many models and many topics][1] 
which compares several topic models using a variety of measures in an attempt to
determine which model should be used in which application.  This evaluation
secondly compares automatic coherence measures as a quick, task free method for
comparing a variety of models.  Below is a detailed series of steps on how to
replicate the results from the paper.

The evaluation setup breaks down into the following steps:

1. Select a corpus and pre-process.
2. Remove stop words, infrequent words, and format the corpus.
3. Perform topic modelling on all documents
4. Compute topic coherence measures for induced topics
5. Compute word similarities using semantic pairing tests
6. Compute Classifier accuracy using induced topics

Each of these steps are automated in the bash scripts provided in this
repository.  To run those scripts read the last section for downloading the
needed components, setting parameters, and then watching the scripts blaze
through the setup.

The rest of this writeup explains each step in more detail than was permitted in
the published paper.

## Selecting the corpus

The evaluation requires the use of a semantically labeled corpus that has a
relatively cohesive focus.  The original paper used all articles from 2003 of
the [New York Times Annotated Corpus][2] provided by the [Linguistics Data Consortium][3].  
Any similarly structured corpus should work.  

The New York Times corpus requires some pre-processing before it can be easily
used in the evaluation.  The original corpus comes in a series of tarballed xml
files where each file looks something like this:

``` xml
<nitf change.date="month day, year" change.time="HH:MM" version="-//IPTC//DTD NITF 3.3//EN">
<head>
  <title>Article Title</title>
  <meta content="Section Name" name="online_sections"/>
</head>
<body>
  <body.contents>
    <block class="full_text">
      <p>Article text</p>
    </block>
  </body.contents>
</body>
</nitf>
```
This leaves out a lot of details, but it covers the key items we will need: (1)
the full text of the article and (2) all online\_sections for the article.
Extracting this can be kinda hairy.  The following snippet gives a gist of how
to extract and format the neccesary data:

``` scala
import scala.xml.XML

val doc = XML.loadFile(file)
val sections = (doc \\ "meta").filter(node => (node \ "@name").text == "online_sections")
                              .map(node => (node \ "@content").text)
                              .mkString(";")
val text = (doc \\ "block").filter(node => (node \ "@class").text == "full_text")
                           .flatMap(node => (node \ "p").map(_.text.replace("\n", " ").trim))
                           .mkString(" ")
```

Before printing the data, we also need to tokenize everything.  We used the Open
NLP MaxEnt tokenizers.  First download the english MaxEnt tokenizer model
[here][4] then do the following before processing each document

``` scala
val tokenizerModel = new TokenizerModel(new FileInputStream(modelFileName))
val tokenizer = new TokenizerME(tokenizerModel)
val stopWords = Source.fromFile(args(1)).getLines.toSet
def acceptToken(token: String) = !stopWords.contains(token)
```

And then do the following to each piece of `text` extracted:

```
val tokenizedText = tokenizer.toLowerCase.tokenize(text).filter(acceptToken).mkString(" ")
printf("%s\t%s\n", sections, tokenizedText)
```

This should generate one line per document in the format


```
section_1(;section_n)+<TAB>doc_text   
```

With properly tokenized text and a series of stop words removed..

## Filtering tokens

In order to limit the memory requirements of our processing steps, we discard
any word that is not in the list of word similarity pairs or the top 100k most
frequent tokens in the corpus.  The following bash lines will accomplish this:

```
cat $oneDocFile | cut -f 2 | tr " " "\n" | sort | uniq -c | \ 
                  sort -n -k 1 -r | head -n 100000 | \
                  awk '{ print $2 }' > $topTokens
cat wordsim*.terms.txt $topTokens | uniq > .temp.txt
mv .temp.txt $topTokens
```

Once we've gotten the top tokens that'll be used during processing, we do one
more filtering of the corpus to reduce each document down to only the accepted
words and discard any documents that contain no useful content words.  Running
[FilterCorpus][4] with the top tokens file and the corpus file will return a
properly filtered corpus.

## Topic Modeling

With all the pre-processing completed, we can now generate topics for the
corpus.  We do this using two different methods (1) Latent Dirichlet Allocation
and (2) Latent Semantic Analsysis.  Unless otherwise stated, we we performed
topic modeling using each method for 1 to 100 topics, and for 110 to 500 topics
with steps of 10.  

### Processing for Latent Dirichlet Allocation

We use [Mallet's][5] fast parallel implementaiton of Latent Dirichlet Allocation
to do the topic modelling.  Since [Mallet's][5] interface does not let us easily
limit the set of tokens or set the indices we want each token to have, we
provide a class to do this: [TopicModelNewYorkTimes][2].  This takes five
arguments

1. The set of content words to represent
2. Number of top words to report for each topic
3. The documents to represent
4. The number of topics
5. A name for the output data.

And we run this with the following command. 

```
scala edu.ucla.sspace.TopicModelNewYorkTimes $topTokens 10 $oneDocFile $nTopics nyt03_LDA_$nTopics
```

for the specified range of topics.  The command will then perform LDA and store
the term by topic matrix in `nyt03_LDA_$nTopics-ws.dat`, the document by
topic matrix in `nyt03_LDA_$nTopics-ds.dat`, and the top 10 words for each
topic in `nyt03_LDA_$nTopics.top10`.

### Processing for Latent Semantic Analysis

Latent Semantic Analysis at it's core decomposes a term by document matrix into
two smaller latent matrices using one of two methods: (1) [Singular Value
Decomposition][6] and (2) [Non-negative Matrix Factorization][7].  We do this in
two steps:

1. Build a weighted term document matrix.
2. Factorize the matrix using either SVD or NMF.

We use the [BuildTermDocMatrix][8] class to perform the first step.  It takes
four arguments:

1. A list of words to represent
2. A feature transformation method, valid options are tfidf, logentropy, and
   none
3. The corpus to represent
4. An output filename

We run this once on our properly formatted corpus using the top set of tokens
using this command


```
scala edu.ucla.sspace.BuildTermDocMatrix $topTokens logentropy $oneDocFile $oneDocFile.mat
```

With the term document matrix, we then decompose it using the
[MatrixFactorNewYorkTimes][9] method, which uses either SVD or NMF to decompose
the matrix and stores a term by latent factor matrix and a document by latent
factor matrix to disk.  A sample run of this looks like:

```
scala edu.ucla.sspace.MatrixFactorNewYorkTimes $oneDocFile.mat nmf 10 nyt03_NMF_10-ws.dat nyt03_NMF_10-ds.dat 
```

Which will decompose the term doc matrix using 10 latent features, or topics,
and store the term by topic matrix in `nyt03_NMF_10-ws.dat` and the document by
topic matrix in `nyt03_NMF_10-ds.dat`.  Because the SVD is deterministic and the
result for 500 topics includes the results for all smaller topics, we do this
just once for the SVD with 500 topics and use the appropriate number of
SVD-based topics later on.

After producing all the decompositions, we extract the top terms for each model
using [ExtractTopTerms][10].  A run of this looks like

```
scala edu.ucla.sspace.ExtractTopTerms $topTokens $nTopics nyt03_NMF_$nTopics-ws.dat > nyt03_NMF_10.top10
```

## Computing Topic Coherence for all topics

Computing the topic coherence depends critically on computing some similarity
value between two words that appear in the same topic.  We do this in a
multi-step process:

1. Compute the list of all words appearing in any topic
2. Compute the Pointwise Mutual Information scores between all listed words
   within an extrenal corpus (for the UCI metric)
3. Compute document Co-Occurence scores for all listed words in the New York
   Times corpus (for the UMass metric)
4. Start a server for each set of scores and query the server for the coherence
   of each topic

To compute the set of all words appearing in any topic, we just use this bash
command:

```
cat *.top10 | tr " " "\n" | sort -u > $allTopicTerms
```

The [ExtractUCIStats][11] class will do just as it says, extract the raw scores
needed for the UCI metric, i.e. Pointwise Mutual Information scores between each
topic word as they appear within a sliding window of K words in an external
corpus.  We use a sliding window of 20 words and we use the [Wackypedia][12]
corpus as our external dataset.  Similarly, [ExtractUMass][13] will extract the
raw scores needed for the UMass metric, i.e. document co-occurence counts for
topic words as they appear in the New York Times corpus.  These two commands
will run theses classes as desired:

```
scala edu.ucla.sspace.ExtractUCIStats $allTopicTerms $uciStatsMatrix $externalCorpusFile
scala edu.ucla.sspace.ExtractUMassStats $allTopicTerms $umassStatsMatrix $oneDocFile
```

Then, for each metric, we startup an [Avro][14] based [CoherenceServer][15] that
will compute the coherence of a topic using the raw scores computed between
individual words.  This server works the same with both sets of scores computed
above, the only change is the input matrix.  We then query the server for each
topic and record the computed coherence.  A key argument for computing the
coherence is the `epsilon` value used to smooth the coherence scores such that
they remain real valued.  These two commands will start the server and query the
server for a set of topics:

```
scala edu.ucla.sspace.CoherenceServer $uciStatsMatrix $allTopicTerms $port &
scala edu.ucla.sspace.SenseCoherence $port $top10File $epsilon "$model $numTopics $metricName"
```

The port value needs to be the same for both commands and you must wait for the
server to full start before running the second command.  `$top10File`
corresponds to the list of top 10 words per topic computed in the previous
section, the third argument is a set of values to be printed for each coherence
score reported, and lastly `$epsilon` is some positive non-zero number.  After
running `SenseCoherence` as above, it should report lines with this format:

```
LDA 10 UCI 1 <score>
LDA 10 UCI 2 <score>
LDA 10 UCI 3 <score>
```

With each line corresponding to a topic with a given id computed by LDA using 10
topics and evaluated by the UCI measure.  For our experiments, we considered
using 1.0 and 1E-12 for epsilon.

## Comparing Word Wimilarities with Semantic Judgements

We compared the reduced word representations against two standard sets of
semantic similarity judgements as our second experiment.  We're including the
sets of semantic similarity judgements with this repository since they are both
publiclly available.  The processing steps remains the same for both sets of
judgements and each topic model.

We use the [Spearman Rank Correlation][16] between humand semantic judgements and
the cosine similarity between latent word representations as the key metric.  A
higher rank correlation, or any other correlation measure, indicates that the
latent feature space better captures relations observed by humand judges.  The
[ComputeCorrelation][17] class will perform these calculations for a single set of
semantic judgments and a term by topic matrix.  We run this with

```
scala edu.ucla.sspace $topTokens nyt03_LDA_10-ws.dat data/wordSim65pairs.tab "LDA 10"
```

Which computes the correlation betwee a LDA based topic model using 10 topics
and the Rubensten and Goodenough dataset and again reports some tag information
when printing the correlation value.  We do this for all topic models computed
and the `data/wordSim65pairs.tab` and `data/combined.tab` semantic judgements
files which correspond to the [Rubenstein and Goodenough][18] dataset and the
[WordSim 353][19] dataset, repsectively.

## Computing Classifier accuracy using latent feature spaces

As our last experiment, we test the each topic model's ability to distinguish
different documents.  Since each represented New York Times document has a broad
semantic label, the section of the paper it was printed in, we evaluate the
representations by training and testing a classifier using the document by topic
features learned by each model.  This evaluation requires just two steps:

1. Forming a training/testing split.
2. Learning a classifier on the training data and testing it with the test data.

We used 10 fold stratified cross fold validation, which insoles splitting the
document by topic space into 10 evenly sized portions which each contain the
same proportion of each New York Times section, for example each fold should
have 60% Opinion documents and 40% World News documents.  

The [FormFolds][20] command will produce these even stratified folds and
simultaneously drop any documents that have more than 1 section label or a
section label that is applied to fewer than 2000 documents.  We drop these
documents to limit the classification task to well represented and unambiguous
section labellings.  We run this command once with

```
cat $oneDocFile | cut -f 1 > $docLabels
scala edu.ucla.sspace.FormFolds $docLabels $numFolds $minLabelCount > $classifierFolds
```

With the folds pre-computed, we then build a classifier for each topic model
over all the folds and compute the average accuracy for the topic model across
all folds.  [StratifiedSchiselClassify][21] will do this for a classifier type and a topic
model.  We run this with:

```
scala edu.ucla.sspace.StratifiedSchiselClassify -d $classifier $ds $docLabels \
                                      $classifierFolds "$model $numTopics $classifier" \
```

`$classifier` can be nb, c45, dt, or me, corresponding to a Naive Bayes, C4.5
tree, ID3 Decision tree, or Maximum Entropy classifier as provided by
[Mallet][5].  Note that in our original experiments, we used the [Avatar][22]
project.  A writeup on how to use this will be forth coming. 


## Using the automated script

The writeup so far has desecrated the steps we used to compute each experiment
in more detail than provided in the original paper.  However, to make this even
easier to replicate, we've provided a [run][23] script that automates the process
as much as possible.  This section describes the minimum number of steps needed
to setup the script and do the processing.  However, since many of the
computations are embarrassingly parallel, we didn't use this __exact__ script to
do our processing.  Where noted, we used the same scala class files and inputs,
but parallelized the large number of runs using [Hadoop Streaming][24].  Since
[Hadoop Streaming][24] can be highly frustrating and finicky, we leave that
parallelizing up to you.

Before using the script, you need to download and prepare a few key files that
we cannot distribute:

1. The [New York Times Annotated Corpus][2] for the [Linguistics Data Consortium][3].  
   After downloading this, unzip the 2003 portion of the corpus.  Then set the
   `nytCorpusDir` variable to point to that directory.  If you've set it up
   properly it should have a subdirectory for each month, each of which has
   subdirectories for each day of that month which holds the articles written in
   that month.
2. Download a [OpenNlp][25] [Maximum Entropy][26] tokenize model from [here][4].  Set
   the `tokenizerModel` variable to the location of this file.
3. Download a stop word file.  We used the [english-stop-words-large.txt][27] file
   provided by the [S-Space][28] package.  Set the `stopWords` variable to the
   location of this file.
4. Download the [Wackypedia][12] corpus and set the `externalCorpusDir` variable
   to it's location.

Once those variables have been set and the data has been downloaded, the script
should run using the same parameters we used in our experiments.  It will
eventually produce a large number of word by topic matrices, document by topic
matrices, list of top words per topic files, and a series of data files for each
experiment that can easily be plotted using R.  

If you wish to change some variables, here's the meaning of each one:

* `numTopTokens`: The number of words in the corpus to represent (not including
  words in the semantic similarity judgements). 
* `numTopWordsPerTopic`: The number of top words to report for each topic
* `transform`: The transformation type used for building the Term Document
  Matrix used by LSA.
* `topicSequence`: A sequence of numbers indicating how many topics to learn for
  each model
* `lastTopc`: The largest number of topics requested
* `exponents`: A sequence of numbers indicating the exponent corresponding to
  each value of epsilon used by the coherence metrics.
* `numFolds`: The number of stratified folds to compute for the classifier
  evaluation
* `minLabelCount`: The minimum number of times each section label should occur
* `port`: The port number for the coherence server.

All other variables used indicate location and names of files generated by the
run script.  If space is a concern, set `topicDir` to a location with a large
amount of disk space, most of the generated results will be stored in that
location.  All other location parameters can be similarly changed as needed
without affecting the run script (please notify us by filing an issue if this
statement turns out to be wrong).

  [1]: https://github.com/fozziethebeat/TopicModelComparison
  [2]: http://www.ldc.upenn.edu/Catalog/catalogEntry.jsp?catalogId=LDC2008T19
  [3]: http://www.ldc.upenn.edu/
  [4]: http://opennlp.sourceforge.net/models-1.5/en-token.bin
  [5]: http://mallet.cs.umass.edu/
  [6]: http://en.wikipedia.org/wiki/Singular_value_decomposition
  [7]: http://en.wikipedia.org/wiki/Non-negative_matrix_factorization
  [8]: https://github.com/fozziethebeat/TopicModelComparison/blob/master/src/main/scala/edu/ucla/sspace/BuildTermDocMatrix.scala
  [9]: https://github.com/fozziethebeat/TopicModelComparison/blob/master/src/main/scala/edu/ucla/sspace/MatrixFactorNewYorkTimes.scala
  [10]: https://github.com/fozziethebeat/TopicModelComparison/blob/master/src/main/scala/edu/ucla/sspace/ExtractTopTerms.scala
  [11]: https://github.com/fozziethebeat/TopicModelComparison/blob/master/src/main/scala/edu/ucla/sspace/ExtractUCIStats.scala
  [12]: http://wacky.sslmit.unibo.it/doku.php?id=corpora
  [13]: https://github.com/fozziethebeat/TopicModelComparison/blob/master/src/main/scala/edu/ucla/sspace/ExtractUMassStats.scala
  [14]: http://avro.apache.org/
  [15]: https://github.com/fozziethebeat/TopicModelComparison/blob/master/src/main/scala/edu/ucla/sspace/CoherenceServer.scala
  [16]: http://en.wikipedia.org/wiki/Spearman's_rank_correlation_coefficient
  [17]: https://github.com/fozziethebeat/TopicModelComparison/blob/master/src/main/scala/edu/ucla/sspace/ComputeCorrelation.scala
  [18]: http://dl.acm.org/citation.cfm?id=365657
  [19]: http://www.cs.technion.ac.il/~gabr/resources/data/wordsim353/  
  [20]: https://github.com/fozziethebeat/TopicModelComparison/blob/master/src/main/scala/edu/ucla/sspace/FormFolds.scala
  [21]: https://github.com/fozziethebeat/TopicModelComparison/blob/master/src/main/scala/edu/ucla/sspace/StratifiedSchiselClassify.scala
  [22]: http://morden.csee.usf.edu/avatar/
  [23]: https://github.com/fozziethebeat/TopicModelComparison/blob/master/run.sh
  [24]: http://wiki.apache.org/hadoop/HadoopStreaming
  [25]: http://opennlp.apache.org/
  [26]: http://opennlp.sourceforge.net/models-1.5/
  [27]: https://github.com/fozziethebeat/S-Space/blob/dev-wordsi/data/english-stop-words-large.txt
  [28]: https://github.com/fozziethebeat/S-Space
