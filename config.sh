#!/bin/bash

# Data parameters.  

# The file containing stop words, formatted with one word per line.
stopWords=~/devel/S-Space/data/english-stop-words-large.txt
# The Open NLP english MaxEnt tokenizer model.
tokenizerModel=en-token.bin

# Location parameters
nytCorpusDir=nyt_dvd
externalCorpusDir=./
externalCorpusFile=nyt03-one-doc-per-line.txt
rngWordPairs=data/wordSim65pairs.tab
wordSim353Pairs=data/combined.tab

# Value parameters
numTopTokens=36000
numTopWordsPerTopic=10
transform=logentropy
exponents="00 12 20"
topicSequence="$(seq -w 1 100) $(seq 110 10 500)"
lastTopic=500
numFolds=10
minLabelCount=200
port=50035

# These paramters refer to temporary files use during processing.  They don't
# need to be changed unless you want to name things different.
topicDir=nyt_topics
oneDocFile=nyt03-one-doc-per-line.txt
docLabels=nyt03-doc-labels.txt
topTokens=nyt03-top-100k-tokens.txt
allTopicTerms=nyt03-all-topic-terms.txt
termDocMatrix=nyt03-term-doc-matrix-transform.matlab.mat
uciStatsMatrix=nyt03-uci-stats.svdlibc-sparse-text.mat
umassStatsMatrix=nyt03-umass-stats.svdlibc-sparse-text.mat
classifierFolds=nyt03-classifier-folds.txt
rngCorrelationResults=wordsim.rng.correlation.dat
wordSim353CorrelationResults=wordsim.rng.correlation.dat
classifierResults=classifier.accuracy.dat

# Aliases for running commands.  These don't need changing.
run="scala -J-Xmx2g -cp target/TopicModelEval-1.0-jar-with-dependencies.jar"
base="edu.ucla.sspace"


