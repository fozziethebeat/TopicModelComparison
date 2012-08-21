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
rngWordPairs=wordSim65pairs.tab
wordSim353Pairs=combined.tab

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
topTokens=nyt03-top-tokens.txt
allTopicTerms=nyt03-all-topic-terms.txt
termDocMatrix=nyt03-term-doc-matrix-transform.matlab.mat
uciStatsMatrix=nyt03-uci-stats.svdlibc-sparse-text.mat
umassStatsMatrix=nyt03-umass-stats.svdlibc-sparse-text.mat
classifierFolds=nyt03-classifier-folds.txt
rngCorrelationResults=wordsim.rng.correlation.dat
wordSim353CorrelationResults=wordsim.353.correlation.dat
classifierResults=classifier.accuracy.dat

# Aliases for running commands.  These don't need changing.
run="scala -J-Xmx2g -cp target/TopicModelEval-1.0-jar-with-dependencies.jar"
base="edu.ucla.sspace"

# Relocate this if block to skip some of the processing steps.  You might want
# to do this if the processing pipeline crashed midway one step and you don't
# want to re-do others.
if [ 0 != 0 ]; then
    echo Skipped steps
fi
exit

### Processing steps

# FIX STEP: Only do once.  The nyt xml files include a now invalid DOCTYPE link
# so we must remove it in a hackish way.  This script will do it.  Comment out
# after doing once.
bash fixnyt03.sh

# Step 1, extract doc labels and tokenized text from the xml files 
$run $base.NYTExtractor $tokenizerModel $stopWords $nytCorpusDir > $oneDocFile

# Step 2, compute the frequence of each word.
cat $oneDocFile | cut -f 2 | tr " " "\n" | sort | uniq -c | \
                  sort -n -k 1 -r | head -n $numTopTokens | \
                  awk '{ print $2 }' > $topTokens


# Step 3, include words in the similarity tests, just for safe keeping and
# compact.
cat data/wordsim*.terms.txt $topTokens | sort -u > .temp.txt
mv .temp.txt $topTokens

# Step 4, re-run through the corpus so each document contains only the valid
# tokens.  Also extract the header labels for each document in it's own file for
# handy keeping.
$run $base.FilterCorpus $topTokens $oneDocFile > $oneDocFile.tmp
mv $oneDocFile.tmp $oneDocFile
cat $oneDocFile | cut -f 1 > $docLabels

# Step 5, build the term doc matrix for LSA methods.  This will save the file as
# a matlab sparse file.
$run $base.BuildTermDocMatrix $topTokens $transform $oneDocFile $termDocMatrix

# Step 6, factorize the term doc matrix using both lsa methods and for a series
# of desired topics.

# for NMF, we do a series of runs, one for each desired topic number.
mkdir -p $topicDir/{nmf,svd,lda}

# This step can easily be parallelized, with one node handling each nmf
# factorization.
for t in $topicSequence; do
    $run $base.MatrixFactorNewYorkTimes $termDocMatrix nmf $t \
                                        $topicDir/nmf/nyt03_NMF_$t-ws.dat \
                                        $topicDir/nmf/nyt03_NMF_$t-ds.dat
done


# For SVD, we can do it just once since the final factorization subsumes all
# smaller factorizations.
$run $base.MatrixFactorNewYorkTimes $termDocMatrix svd $lastTopic \
                                    $topicDir/svd/nyt03_SVD_$lastTopic-ws.dat \
                                    $topicDir/svd/nyt03_SVD_$lastTopic-ds.dat
 
# Now tear out the smaller factorizations.
for t in $topicSequence; do
    [ $t == $lastTopic ] && continue
    $run $base.ReduceSVD $t $topicDir/svd/nyt03_SVD_$lastTopic-ws.dat $topicDir/svd/nyt03_SVD_$t-ws.dat 
    #$run $base.ReduceSVD $t $topicDir/svd/nyt03_SVD_$lastTopic-ds.dat $topicDir/svd/nyt03_SVD_$t-ds.dat 
done


# Also extract the top terms for each reduced word space.
for t in $topicSequence; do
    $run $base.ExtractTopTerms $topTokens $numTopWordsPerTopic $topicDir/nmf/nyt03_NMF_$t-ws.dat > $topicDir/nmf/nyt03_NMF_$t.top10
    $run $base.ExtractTopTerms $topTokens $numTopWordsPerTopic $topicDir/svd/nyt03_SVD_$t-ws.dat > $topicDir/svd/nyt03_SVD_$t.top10
done

# Step 7, factorize using LDA, this will create both factorized matrices and the
# top 10 word lists.
# This step can easily be parallelized, with one node handling each LDA 
# factorization.
for t in $topicSequence; do
    $run $base.TopicModelNewYorkTimes $topTokens $numTopWordsPerTopic $oneDocFile $t $topicDir/lda/nyt03_LDA_$t
done

# Create a list of all words that appeared in the top 10 list for every model
# computed.  This list will be used to compute the words needed for all
# coherence scores and allow the co-occurrence matrices to remain small.
cat $topicDir/{lda,nmf,svd}/*.top10 | tr " " "\n" | sort -u > $allTopicTerms

# Extract a matrix that represents co-occurrence counts needed by the two
# coherence metrics.  The UCI measure uses pmi counts from the training corpus
# and the UMass metric uses counts from an external corpus.
# These two steps could be easily parallelized using Scoobi or anothe map reduce
# framework if so desired, but isn't terribly neccesary.
$run $base.ExtractUCIStats $allTopicTerms $uciStatsMatrix $externalCorpusFile
$run $base.ExtractUMassStats $allTopicTerms $umassStatsMatrix $oneDocFile

# define a generic function that computes the coherence for each topic for a
# model and reports the results in a R readable format.
function extractCoherence() {
epsilon=$1
metricName=$2
for top10File in $topicDir/{lda,nmf,svd}/*.top10; do
    modelFile=`echo $top10File | sed "s/.*nyt03_\(.*\).top10/\1/"`
    model=`echo $modelFile | cut -d "_" -f 1`
    numTopics=`echo $modelFile | cut -d "_" -f 2`
    $run $base.SenseCoherence $port $top10File $epsilon "$model $numTopics $metricName"
done 
}

function computeAllCoherence() {
dataMatrix=$1
metricName=$2

# First start an avro server that will report the coherence scores for a topic.
# It will start up in the background on the specified port.
$run $base.CoherenceServer $dataMatrix $allTopicTerms $port &

read -p "Press enter when server is fully started"

# Now compute the coherence using the uci metric for every model and various
# values for epsilon.
for exponent in $exponents; do
    extractCoherence 1E-$exponent $metricName >> $topicDir/coherence.scores.$exponent.dat
done

# Kill the uci metric server
kill `ps -f --no-heading  -C java | tr -s " " "\t" | cut -f 2`
}

for exponent in $exponents; do
    echo "Model Topics Metric Topic Coherence" > $topicDir/coherence.scores.$exponent.dat
done

# Compute the coherence scores using the UCI metric
computeAllCoherence $uciStatsMatrix UCI
# Compute the coherence scores using the UMass metric
computeAllCoherence $umassStatsMatrix UMass

function computeWordSimCorrelation() {
wordPairs=$1
echo "Model Topics Correlation"
#for ws in $topicDir/{lda,nmf,svd}/*-ws.dat; do
#    modelFile=`echo $ws | sed "s/.*nyt03_\(.*\)-ws.dat/\1/"`
#    model=`echo $modelFile | cut -d "_" -f 1`
#    numTopics=`echo $modelFile | cut -d "_" -f 2`
#    $run $base.ComputeCorrelation $topTokens $ws $wordPairs "$model $numTopics"
#done
for m in "LDA lda" "SVD svd" "NMF nmf"; do
    for t in $topicSequence; do
        Model=`echo $m | cut -d " " -f 1`
        model=`echo $m | cut -d " " -f 2`
        echo $Model $model $t $wordPairs
    done
done > screamInput/WordSimCorrelation
scream src/main/scream/WordSimCorrelation.json > /dev/null
hadoop fs -cat output/WordSimCorrelation/part* 
}

mkdir -p screamInput
computeWordSimCorrelation $rngWordPairs > $topicDir/$rngCorrelationResults
#computeWordSimCorrelation $wordSim353Pairs > $topicDir/$wordSim353CorrelationResults

$run $base.FormFolds $docLabels $numFolds $minLabelCount > $classifierFolds

fi

# This part can be parallelized easily, with one node handling each run of a
# classifier.
echo "Model Topics Classifier Accuracy" > $topicDir/$classifierResults
#for ds in $topicDir/{lda,nmf,svd}/*-ds.dat; do
#    modelFile=`echo $ds | sed "s/.*nyt03_\(.*\)-ds.dat/\1/"`
#    model=`echo $modelFile | cut -d "_" -f 1`
#    numTopics=`echo $modelFile | cut -d "_" -f 2`
#    for classifier in nb c45 dt me; do
#        $run $base.SchiselClassify -d $classifier $ds $docLabels \
#                                      $classifierFolds "$model $numTopics $classifier" | tail -n 1
#    done
#done >> $topicDir/$classifierResults
for m in "LDA lda" "SVD svd" "NMF nmf"; do
    Model=`echo $m | cut -d " " -f 1`
    model=`echo $m | cut -d " " -f 2`
    for t in $topicSequence; do
        for classifier in nb c45 dt me; do
            echo $Model $model $t $classifier
        done
    done
done > screamInput/Classifier
scream src/main/scream/Classifier.json > /dev/null
#hadoop fs -cat output/Classifier/part* >> $topicDir/$classifierResults

exit
