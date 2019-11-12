FROM denvazh/gatling:3.2.1

ADD https://gerrit-ci.gerritforge.com/job/gatling-git-sbt-master/52/artifact/target/scala-2.12/gatling-git-extension.jar /opt/gatling/lib/

COPY ./src/test/scala/com/google/gerrit/scenarios /opt/gatling/user-files/simulations
#COPY ./src/test/resources/logback.xml /opt/gatling/conf
COPY ./src/test/resources/application.conf /opt/gatling/conf
COPY ./src/test/resources/data /opt/gatling/user-files/resources/data

RUN mkdir -p /opt/gatling/results

ENTRYPOINT ["/opt/gatling/bin/gatling.sh"]
