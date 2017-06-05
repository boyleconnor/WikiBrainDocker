#
# Oracle Java 8 Dockerfile
#
# https://github.com/dockerfile/java
# https://github.com/dockerfile/java/tree/master/oracle-java8
#

# Pull base image.
FROM ubuntu

# Install Java.
RUN \
  apt-get update && \
  apt-get --assume-yes install git && \
  echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
  apt-get --assume-yes install software-properties-common && \
  add-apt-repository -y ppa:webupd8team/java && \
  apt-get update && \
  apt-get install -y oracle-java8-installer && \
  rm -rf /var/lib/apt/lists/* && \
  rm -rf /var/cache/oracle-jdk8-installer

# Define commonly used JAVA_HOME variable
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

# Install maven
RUN apt-get update
RUN apt-get install -y maven

# Define working directory.
WORKDIR /home
ADD postgres.conf postgres.conf
RUN git clone https://github.com/shilad/wikibrain.git ./wikibrain
RUN git clone https://github.com/shilad/CartoExtractor.git ./CartoExtractor


# Define commonly used JAVA_HOME variable
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

# Maven comes in to compile via the pom.xml file (hopefully)
WORKDIR /home/wikibrain

# Checkout <develop> branch in Git
RUN git checkout develop

# Maven Stuff
RUN mvn -f wikibrain-utils/pom.xml clean compile exec:java -Dexec.mainClass=org.wikibrain.utils.ResourceInstaller

# Install PostgreSQL
WORKDIR /home/
ADD apt.postgresql.org.sh script.sh
RUN chmod 111 script.sh && yes | ./script.sh
RUN DEBIAN_FRONTEND=noninteractive apt-get install -y -q postgresql-9.5 pgadmin3
RUN apt-get install -y postgresql-9.5-postgis-2.3
RUN service postgresql start

#Update prostgresql settings and config file with overwrite:
RUN cp postgres.conf /etc/postgresql/9.5/main
ls -l /etc/postgresql/9.5/main

# Define default command.
CMD bash
