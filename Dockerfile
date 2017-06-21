#
# CartoExtractor & WikiBrain Container
# 
# When running an image built with this Dockerfile, it is required to define
# the following environment variables:
#
# MEM:
#     The number of megabytes, appended with 'm' to allocate to the JVM when
#     running WikiBrain's loader, e.g. '8000m'. If this is set too low, the
#     diagnostic stage of WikiBrain's loader will fail and produce a helpful
#     error message containing the necessary amount of memory. NOTE: if you set
#     MEM higher than the maximum amount of memory available to Docker (as
#     defined in your Docker preferences), the loader will crash with an
#     extremely vague and unhelpful error message (code 137).
#
# WIKILANG:
#     The (usually two-letter) language code of the Wikipedia from which you'd
#     like to load pages, e.g. 'en' or 'simple'.
#
# To get the eventual output files from CartoExtractor, you'll need to set up a
# "volume" at run time to be shared with the host. This can be done with the
# '-v HOST_DIR:/output' where HOST_DIR is a path on the host to a directory
# (to be made if it doesn't exist) where the output files will be sent.
#
# The built image should also be run with the following options specifying
# shared memory parameters, which are needed for WikiBrain:
# 
#     --sysctl kernel.shmmax=64205988352
#     --sysctl kernel.shmall=15675290
#
# Running the image will automatically start by running WikiBrain loader and
# CartoExtractor. If the '-it' option is given at runtime, it will then give
# the user an interactive shell into the container.
#
# The following two lines are an example of how to build an image and run a
# container from this Dockerfile:
#
# docker build -t CartoContainer .
# docker run --sysctl kernel.shmmax=64205988352 --sysctl kernel.shmall=15675290 -e WIKILANG=$1 -e MEM=9g -v ./output:/output -it CartoContainer


# Pull Ubuntu base image.
FROM ubuntu


# Install Java. Source: TODO: track down and add source
RUN apt-get update
# Add Oracle Repository
RUN echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections
RUN apt-get --assume-yes install software-properties-common
RUN add-apt-repository -y ppa:webupd8team/java
# TODO: check if below update is meaningful
RUN apt-get update
# Install Java 8 package from Oracle Repository
RUN apt-get install --assume-yes oracle-java8-installer
RUN rm -rf /var/lib/apt/lists/*
RUN rm -rf /var/cache/oracle-jdk8-installer
# Define Java_Home
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle
# Install maven
RUN apt-get update
RUN apt-get install --assume-yes maven


## Clone WikiBrain and CartoExtractor from Git
# Install Git
RUN apt-get --assume-yes install git
WORKDIR /home
# Clone WB and CE to appropriate paths
RUN git clone https://github.com/shilad/wikibrain.git ./wikibrain
RUN git clone https://github.com/shilad/CartoExtractor.git ./CartoExtractor


WORKDIR /home/wikibrain
# Checkout <develop> branch in Git
RUN git checkout develop
# Maven Stuff TODO: Ask Shilad to label this command
RUN mvn -f wikibrain-utils/pom.xml clean compile exec:java -Dexec.mainClass=org.wikibrain.utils.ResourceInstaller

# Install PostgreSQL
WORKDIR /home/
ADD apt.postgresql.org.sh script.sh
RUN chmod 111 script.sh && yes | ./script.sh
RUN DEBIAN_FRONTEND=noninteractive apt-get install -y -q postgresql-9.5
RUN apt-get install -y postgresql-9.5-postgis-2.3

# Update Postgresql settings:
ADD postgres.conf postgres.conf
RUN cp postgres.conf /etc/postgresql/9.5/main/postgres.conf

# Add Pre-Loaded WikiBrain
# ADD wikibrain wikibrain
# ADD postgresql postgresql

# Add Custom WikiBrain Configuration File
WORKDIR /home/wikibrain/
ADD customized.conf_template customized.conf_template

# Add script to create appropriate users and DBs in Postgres
ADD postgres_setup.sh postgres_setup.sh

# Add pre-downloaded English Wikipedia
# ADD download en/download


CMD \
    ## Move to host-linked dir
    cd .. && \
    cp -r wikibrain /host/wikibrain && \
    cp -r CartoExtractor /host/CartoExtractor && \
    cd /host/wikibrain && \

    ## Start up and configure for PostgreSQL
    # Copy PostgreSQL data to host directory
    mv /var/lib/postgresql /host && \
    # Edit PostgreSQL conf to reflect moved data
    sed "s/\/var\/lib\/postgresql/\/host\/postgresql/" /etc/postgresql/9.5/main/postgresql.conf > /etc/postgresql/9.5/main/postgresql.conf_tmp && \
    mv /etc/postgresql/9.5/main/postgresql.conf_tmp /etc/postgresql/9.5/main/postgresql.conf && \

    # Start psql daemon
    service postgresql start && \
    # Add appropriate db & user to psql
    sh postgres_setup.sh && \
    # Generate (wiki) language-appropriate psql conf file for WikiBrain
    sed "s/<WIKILANG>/$WIKILANG/" customized.conf_template > customized.conf && \

    # Run WikiBrain's loader
    # ./wb-java.sh -Xmx$MEM org.wikibrain.Loader -l $WIKILANG -c customized.conf && \

    # Run CartoExtractor, outputting to /output (recommended to share this with host using '-v')
    # cd ../CartoExtractor && \
    # MAVEN_OPTS="-Xmx9000m" mvn compile -e exec:java -Dexec.mainClass="info.cartograph.Extractor" -Dexec.args="-o /output --base-dir ../wikibrain/$WIKILANG -r 1 -c ../wikibrain/customized.conf"
    # Provide shell (in case user wants one, must be run with "-it" option)
    bash
