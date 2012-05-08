#!/bin/sh

SRC=$(ls gerrit-plugin-api/target/gerrit-plugin-api-*-sources.jar)
VER=${SRC#gerrit-plugin-api/target/gerrit-plugin-api-}
VER=${VER%-sources.jar}
JAR=gerrit-plugin-api/target/gerrit-plugin-api-$VER.jar

type=release
case $VER in
*-SNAPSHOT)
  echo >&2 "fatal: Cannot deploy $VER"
  echo >&2 "       Use ./tools/version.sh --release && mvn clean package"
  exit 1
  ;;
*-[0-9]*-g*) type=snapshot ;;
esac
URL=s3://gerrit-api@commondatastorage.googleapis.com/$type

echo "Deploying gerrit-plugin-api $VER to $URL"
mvn deploy:deploy-file \
  -DgroupId=com.google.gerrit \
  -DartifactId=gerrit-plugin-api \
  -Dversion=$VER \
  -Dpackaging=jar \
  -Dfile=$JAR \
  -DrepositoryId=gerrit-api-repository \
  -Durl=$URL

mvn deploy:deploy-file \
  -DgroupId=com.google.gerrit \
  -DartifactId=gerrit-plugin-api \
  -Dversion=$VER \
  -Dpackaging=java-source \
  -Dfile=$SRC \
  -Djava-source=false \
  -DrepositoryId=gerrit-api-repository \
  -Durl=$URL
