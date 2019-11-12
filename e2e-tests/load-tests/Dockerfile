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

COPY ./src/test/scala/com/google/gerrit/scenarios /opt/gatling/user-files/simulations
COPY ./src/test/resources/application.conf /opt/gatling/conf
COPY ./src/test/resources/data /opt/gatling/user-files/resources/data

RUN mkdir -p /opt/gatling/results

ENTRYPOINT ["/opt/gatling/bin/gatling.sh"]
