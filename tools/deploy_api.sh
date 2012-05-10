#!/bin/sh

SRC=$(ls gerrit-plugin-api/target/gerrit-plugin-api-*-sources.jar)
VER=${SRC#gerrit-plugin-api/target/gerrit-plugin-api-}
VER=${VER%-sources.jar}

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


echo "Deploying $type gerrit-extension-api $VER"
mvn deploy:deploy-file \
  -DgroupId=com.google.gerrit \
  -DartifactId=gerrit-extension-api \
  -Dversion=$VER \
  -Dpackaging=jar \
  -Dfile=$module/target/gerrit-extension-api-$VER-all.jar \
  -DrepositoryId=gerrit-api-repository \
  -Durl=$URL

mvn deploy:deploy-file \
  -DgroupId=com.google.gerrit \
  -DartifactId=gerrit-extension-api \
  -Dversion=$VER \
  -Dpackaging=java-source \
  -Dfile=$module/target/gerrit-extension-api-$VER-all-sources.jar \
  -Djava-source=false \
  -DrepositoryId=gerrit-api-repository \
  -Durl=$URL


echo "Deploying $type gerrit-plugin-api $VER"
mvn deploy:deploy-file \
  -DgroupId=com.google.gerrit \
  -DartifactId=gerrit-plugin-api \
  -Dversion=$VER \
  -Dpackaging=jar \
  -Dfile=$module/target/gerrit-plugin-api-$VER.jar \
  -DrepositoryId=gerrit-api-repository \
  -Durl=$URL

mvn deploy:deploy-file \
  -DgroupId=com.google.gerrit \
  -DartifactId=gerrit-plugin-api \
  -Dversion=$VER \
  -Dpackaging=java-source \
  -Dfile=$module/target/gerrit-plugin-api-$VER-sources.jar \
  -Djava-source=false \
  -DrepositoryId=gerrit-api-repository \
  -Durl=$URL
