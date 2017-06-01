#
# Oracle Java 8 Dockerfile
#
# https://github.com/dockerfile/java
# https://github.com/dockerfile/java/tree/master/oracle-java8
#

# Pull base image.
FROM dockerfile/java:oracle-java8

# Install maven
RUN apt-get update
RUN apt-get install -y maven

# Define working directory.
WORKDIR /wikibrain

add pom.xml
RUN ["mvn", "dependency:resolve"]
RUN ["mvn", "verify"]

# Define commonly used JAVA_HOME variable
ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

# Define default command.
CMD ["bash"]
