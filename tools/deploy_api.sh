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

echo "Deploying API $VER to $URL"
for module in gerrit-extension-api gerrit-plugin-api
do
  mvn deploy:deploy-file \
    -DgroupId=com.google.gerrit \
    -DartifactId=$module \
    -Dversion=$VER \
    -Dpackaging=jar \
    -Dfile=$module/target/$module-$VER.jar \
    -DrepositoryId=gerrit-api-repository \
    -Durl=$URL

  mvn deploy:deploy-file \
    -DgroupId=com.google.gerrit \
    -DartifactId=$module \
    -Dversion=$VER \
    -Dpackaging=java-source \
    -Dfile=$module/target/$module-$VER-sources.jar \
    -Djava-source=false \
    -DrepositoryId=gerrit-api-repository \
    -Durl=$URL
done
