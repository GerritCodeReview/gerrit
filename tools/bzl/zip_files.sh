#!/bin/sh
set -e

ZIP_NAME=$1
shift
DIR_NAME=$1
shift
TMP_DIR=$1
shift

OUTPUT_DIR=$(pwd)

TZ=UTC
export TZ
mkdir -p $TMP_DIR
rsync -r -R -L "$@" $TMP_DIR/
echo $OUTPUT_DIR/$ZIP_NAME
echo $ZIP_NAME
cd $TMP_DIR
cd $DIR_NAME
find . -exec touch -t 198001010000 '{}' ';'
zip -Xqr $OUTPUT_DIR/$ZIP_NAME *
cd ..
rm -rf $TMP_DIR
