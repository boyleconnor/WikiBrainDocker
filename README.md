# WikiBrainDocker
# Overview
The WikiBrainDocker is a docker image based on the wikibrain project and the CartoExtractor project that utilizes wikibrains. Based on a Ubuntu image, the WikiBrainDocker automatically downloads and installs all of the prerequisites for using WikiBrains and CartoExtractor. In order to run the docker image, one must have docker installed and configured. Configuration requires at least 4 GB of RAM. 

# Setup
Download and install docker through your web browser of choice. Go to Docker Preferences, and change the memory allocation for an image to 4 Gb from its default value. 

Download the WikiBrainDocker zip file, and unzip it to a local folder of choice. Make sure that you can easily access this folder path. 

Open a bash or terminal, and change your directory so that you are in the wikibraindocker folder.

# Running

Type in ./buildrun.sh in order to automatically build the docker image and run it. upon completion the bash for the docker container will be available. 

Alternatively one can use "docker build -t IMAGENAME ." as a way of building the docker image and "docker run --sysctl kernel.shmmax=64205988352 --sysctl kernel.shmall=15675290 -v HOMEPATHFOROUTPUTFILES:/output --name outputFiles -it IMAGENAME" to then begin to run the shell.

The dockerfile does the following:
1. Install Maven
2. Install Java 8 for running java files
3. Checkout and configure the wikibrain and cartoextractor repositories
4. Install postgreSQL and postGIS.
5. Compile and generate data from the Wikibrain loader file for the simple english Wikipedia database
6. Use that data in order to generate tsv files for use in visualization using the CartoExtractor extractor functionality.
7. Save the generated files in a volume accessible to other containers and the local host
8. Start up a postgreSQL server for user usage.
9. Run a bash terminal for user input in the event that they'd like to modify the parameters.

# Runtime defaults 

This runthrough automatically does the following:
1. the kernel shmall value is set to 15675290 bytes.
2. The kernel shmmax value is set to 64205988352 bytes.
3. The versions for software installed are: 
  - Java 8
  - PostgreSQL version 9.5
  - PostGIS 2.3
  - Ubuntu is based on the most current docker image available at runtime
  
4. Custom setting changes to postgreSQL can be found in the postgres.conf file. These settings overwrite the default choices during postgreSQL installation. 
5. The loader program memory allocation is set to 3.5GB at runtime. Doing less than this may lead to errors at runtime. 

When running the loader and extractor files on your own, the following commands are done as default: 
- ./wb-java.sh -Xmx3500m org.wikibrain.Loader -l simple
- exec:java -Dexec.mainClass="info.cartograph.Extractor" -Dexec.args="-o /output --base-dir ../wikibrain -r 1"

To attempt to use other versions of wikipedia beyond simple english, change the -l flag when running the wikibrain loader. 
To change the output volume, change the -o flag when you run the cartograph Extractor. It's important to note that only the /output directory is a volume that's also mounted to a directory on the host computer. Changing this directory may lead to being unable to find your files on the host machine after running. 

By default, the /output directory is mapped to /Public/generatedFiles. This directory can be changed in the buildrun.sh script before running. 

Additionally, the build currently defaults to using caches during the docker run command, if you wish to start the image fresh (in the event that you'd like to save output again for example in a different directory), you'll need to add the --no-cache tag to your docker build function in ./buildrun.sh. Do note that this will lead to the docker run function taking significantly longer on subsequent runs as it won't have cache to refer to to speed up the run time. 
