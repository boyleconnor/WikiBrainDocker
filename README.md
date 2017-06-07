# WikiBrainDocker
# Overview
The WikiBrainDocker is a docker image based on the wikibrain project and the
CartoExtractor project that utilizes wikibrains. Based on a Ubuntu image, the
WikiBrainDocker automatically downloads and installs all of the prerequisites
for using WikiBrains and CartoExtractor. In order to run the docker image, one
must have docker installed and configured. Configuration requires at least 4 GB
of RAM. 

# Setup
Download and install docker through your web browser of choice. Go to Docker
Preferences, and change the memory allocation for an image to 4 Gb from its
default value. 

Download the WikiBrainDocker zip file, and unzip it to a local folder of
choice. Make sure that you can easily access this folder path. 

Open a bash or terminal, and change your directory so that you are in the
wikibraindocker folder.

# Running

Type in ./buildrun.sh in order to automatically build the docker image and run
it. upon completion the bash for the docker container will be available. 

Alternatively, one can use "docker build -t IMAGENAME ." where IMAGENAME is the
name you want to give your image (e.g. "WikibrainImage") as a way of building
the docker image and "docker run --sysctl kernel.shmmax=64205988352 --sysctl
kernel.shmall=15675290 -v HOMEPATHFOROUTPUTFILES:/output --name outputFiles -it
IMAGENAME" to then begin to run the shell.

The dockerfile does the following when it builds the image:
1. Install Maven
2. Install Java 8
3. Check out and configure the wikibrain and cartoextractor repositories
4. Install postgreSQL and postGIS

When a user runs the built image, it first does the following:

5. Start up a PostgreSQL server daemon
5. Add a "wikibrain" user and a "wikibrain\_en" database in Postgresql
6. Compile and generate data from the Wikibrain loader file for the simple
   english Wikipedia database
7. Use that data in order to generate tsv files for use in visualization using
   the CartoExtractor extractor functionality.
8. Save the generated files in a volume (TODO: /path/to/volume/) accessible to
   other containers and the local host
9. Run a bash terminal for user input in the event that they'd like to modify
   the parameters.

# Runtime defaults 

The "docker run" command in buildrun.sh automatically does the following:
1. Set the kernel shmall value to 15.6 megabytes.
2. Set the kernel shmmax value to 64.2 gigabytes.
3. TODO: Why is this under "Runtime defaults"? The versions for software
   installed are: 
  - Java 8
  - PostgreSQL version 9.5
  - PostGIS 2.3
  - Ubuntu is based on the most current docker image available at runtime
4. Custom setting changes to postgreSQL can be found in the postgres.conf file.
   These settings overwrite the default choices.
5. The loader program memory allocation is set to 3.5 gigabytes at runtime.
   Allcoating less than this amount may lead to errors at runtime. 

When running the loader and extractor files on your own, the following commands
are done as default: 
-     ./wb-java.sh -Xmx3500m org.wikibrain.Loader -l simple
-     exec:java -Dexec.mainClass="info.cartograph.Extractor" -Dexec.args="-o /output --base-dir ../wikibrain -r 1"

To use versions of wikipedia other than simple english, change the string after
the -l flag when running the wikibrain loader.  To change the output volume,
change the -o flag when you run the cartograph Extractor. It's important to
note that only the /output directory is a volume that's also mounted to a
directory on the host computer. Changing this directory may lead to being
unable to find your files on the host machine after running. 

By default, the /output directory is mapped to /Public/generatedFiles. This
directory can be changed in the buildrun.sh script before running. 
