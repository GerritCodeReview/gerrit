#!/bin/sh

JAR=$(ls gerrit-pluginapi-ssh/target/gerrit-pluginapi-ssh-*.jar)
VER=${JAR#gerrit-pluginapi-ssh/target/gerrit-pluginapi-ssh-}
VER=${VER%.jar}

echo "Deploying gerrit-pluginapi-ssh $VER ..."
mvn deploy:deploy-file \
  -DgroupId=com.google.gerrit \
  -DartifactId=gerrit-pluginapi-ssh \
  -Dversion=$VER \
  -Dpackaging=jar \
  -Dfile=$JAR \
  -DrepositoryId=gerrit-maven-repository \
  -Durl=dav:https://gerrit-maven-repository.googlecode.com/svn
