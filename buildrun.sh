#!/bin/bash
docker build --no-cache -t generateData .
echo "build successful"
docker run --sysctl kernel.shmmax=64205988352 --sysctl kernel.shmall=15675290 -v /tmp/generatedFiles:/output --name outputFiles -it generateData
echo "running shell succcesful"
