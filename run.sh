#!/bin/bash
rm -rf output
rm -rf host
docker build -f DockerfileMaster -t wikibraindocker .
docker run --sysctl kernel.shmmax=64205988352 --sysctl kernel.shmall=15675290 -e WIKILANG=$1 -e MEM=9g -v $PWD/host:/host -v $PWD/output:/output wikibraindocker
