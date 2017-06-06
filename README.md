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
8. Run a bash terminal for user input in the even that they'd like to modify the parameters.
