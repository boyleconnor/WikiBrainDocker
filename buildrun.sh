#!/bin/bash
docker build -t test .
echo "build successful"
#docker restart test
#echo "restart successful"
docker run --sysctl kernel.shmmax=64205988352 --sysctl kernel.shmall=15675290 -v /tmp/BonobosIncorporatedBusinessFiles:/output -it test
echo "running shell succcesful"
