#!/bin/sh

# the ndk must include c++ libraries, crystax version of the ndk works
NDK=$HOME/opt/android-ndk

#----
set -e
PROJ_NAME=$(basename $(dirname $PWD))

mkdir -p $NDK/apps/$PROJ_NAME
cat <<EOF >$NDK/apps/$PROJ_NAME/Application.mk
APP_PROJECT_PATH := $(dirname $PWD)
APP_MODULES      := SidPlayerJNI
#
EOF

echo "----"
cat $NDK/apps/$PROJ_NAME/Application.mk
echo "----"

cd $NDK
make APP=$PROJ_NAME

