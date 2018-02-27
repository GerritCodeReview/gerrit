FROM gerritforge/gerrit-ci-slave-bazel:debian

ARG UPGRADE_SERVER

ADD build.sh /

USER jenkins

ENTRYPOINT /build.sh
