#!/bin/bash

source ./config.sh

inductionFeatures="woc"
inductionModels="hac-avg kmeans eigen"
inductionSizes="5 10 15 10"
inductionDir=data/wiki-contexts/
inductionBase=wiki-contexts

for feature in $inductionFeatures; do
    for model in $inductionModels; do
        for k in $inductionSizes; do
            $run $base.DisambiguateCorpus $oneDocFile \
                                          $oneDocFile.$f.$m.$k.txt \
                                          $topTokens \
                                          10 \
                                          $inductionDir/${inductionBase}*.$f.$m.$k.prototypes
        done
    done
done
