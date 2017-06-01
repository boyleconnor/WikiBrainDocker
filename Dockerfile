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
    echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
    apt-get --assume-yes install software-properties-common && \
    add-apt-repository -y ppa:webupd8team/java && \
    apt-get update && \
    apt-get install -y oracle-java8-installer && \
    rm -rf /var/lib/apt/lists/* && \
    rm -rf /var/cache/oracle-jdk8-installer


  # Define working directory.
  WORKDIR /data

  # Define commonly used JAVA_HOME variable
  ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

  # Install maven
  RUN apt-get update
  RUN apt-get install -y maven

  # Define working directory.
  WORKDIR /wikibrain

  ADD pom.xml /wikibrain/pom.xml
  RUN sh
  RUN ["mvn", "dependency:resolve"]
  RUN ["mvn", "verify"]

  # Define commonly used JAVA_HOME variable
  ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

  # Define default command.
  CMD ["bash"]
