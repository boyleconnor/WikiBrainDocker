#!/bin/bash
docker build -t generatedata .
echo "build successful"
docker run --sysctl kernel.shmmax=64205988352 --sysctl kernel.shmall=15675290 -e WIKILANG=$1 -e MEM=9g -v output:/output -it generatedata
