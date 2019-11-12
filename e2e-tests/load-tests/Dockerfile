FROM denvazh/gatling:3.2.1

ARG gatling_git_version=1.0.9
RUN apk add --no-cache maven
RUN mvn dependency:get \
        -DgroupId=com.gerritforge \
        -DartifactId=gatling-git_2.12 \
        -Dversion=$gatling_git_version \
        -Dtype=pom
RUN mvn dependency:copy-dependencies \
        -f /root/.m2/repository/com/gerritforge/gatling-git_2.12/$gatling_git_version/gatling-git_2.12-$gatling_git_version.pom \
        -DoutputDirectory=/opt/gatling/lib/
RUN mvn dependency:get \
        -Dartifact=com.gerritforge:gatling-git_2.12:$gatling_git_version:jar \
        -Ddest=/opt/gatling/lib/gatling-git.jar

ARG gatling_home=/home/gatling
RUN addgroup -g 1000 -S appgroup && \
        adduser -u 1000 -S gatling -G appgroup -h $gatling_home
RUN cp -R /opt/gatling/* $gatling_home && \
        chown -R gatling:appgroup $gatling_home

WORKDIR $gatling_home
USER gatling

COPY ./src/test/scala/com/google/gerrit/scenarios $gatling_home/user-files/simulations
COPY ./src/test/resources/application.conf $gatling_home/conf
COPY ./src/test/resources/data $gatling_home/user-files/resources/data

ENV GATLING_HOME=$gatling_home

ENTRYPOINT ["/home/gatling/bin/gatling.sh"]
