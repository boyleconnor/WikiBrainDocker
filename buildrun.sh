#!/bin/bash
docker build -t generatedata .
echo "build successful"
docker run --sysctl kernel.shmmax=64205988352 --sysctl kernel.shmall=15675290 -v /Public/generatedFiles:/output --name outputFiles -it generatedata
echo "running shell succcesful"
