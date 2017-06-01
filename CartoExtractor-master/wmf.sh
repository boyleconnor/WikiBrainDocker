#!/bin/bash

mvn clean compile exec:java -Dexec.mainClass="info.cartograph.Extractor" -Dexec.args="-v 2016-03-01_2016-03-07_wikidata_200 -o wmf -c en.conf"
