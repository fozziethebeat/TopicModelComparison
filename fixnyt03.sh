#!/bin/bash

for xml in `find nyt_dvd/ -type f -name "*.xml"`; do
    grep -v "DOCTYPE nitf" $xml > $xml.tmp
    mv $xml.tmp $xml
done
