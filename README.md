# WikiBrainDocker

# Overview

WikiBrainDocker is a dockerfile for the purpose of extracting
Cartograph-compatible data files from a Wikipedia of a given language. It is
based on the default Ubuntu Docker image, and automatically downloads and
installs all prerequisites for using WikiBrain and CartoExtractor. It
automatically installs and runs CartoExtractor and WikiBrain.  It then runs
WikiBrain's loader on a Wikipedia of any language (specified by user), and runs
the CartoExtractor pipeline. In order to run docker image, docker must be
installed.

# Installation

First, download and install Docker if it is not already installed. Then, go to
Docker preferences, and under the "Advanced" tab, change the memory allocation
to at least 10.0 GB.

Download WikiBrainDocker as a zip file and unzip it. Alternatively, you can
clone it with Git. Open a terminal window, and change your working directory to
the uncompressed WikiBrainDocker folder.

# Running

If you are on a unix system and are looking for ease of use, simply run the
following command:

    ./run.sh <WIKILANG>

where \<WIKILANG\> is the (usually two-letter) language code of the Wikipedia
you'd like to download from, e.g. "en" for English or "zh" for Chinese (without
the quotes). For example:

    ./run.sh en

will download and extract the whole English Wikipedia, then output
Cartograph-compatible data (in the form of .tsv files) to ./output (where . is
the current working directory).

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
5. The JVM memory allocation is set to 3.5 gigabytes (at runtime).
   Allocating less than this amount may lead to errors at runtime. 

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
