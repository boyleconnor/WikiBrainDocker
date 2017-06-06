#!/bin/bash
docker build --no-cache -t test .
echo "build successful"
docker run --sysctl kernel.shmmax=64205988352 --sysctl kernel.shmall=15675290 -v /tmp/BonobosIncorporatedBusinessFiles:/output --name outputFiles -it test
echo "running shell succcesful"
