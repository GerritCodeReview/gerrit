FROM openjdk:11 AS artifacts
COPY /artifacts/ /artifacts/
RUN mkdir -p /artifacts/bin /artifacts/plugins /artifacts/lib

FROM openjdk:11
ENV GERRIT_USER gerrit
ENV USER_HOME /home/$GERRIT_USER
ENV GERRIT_SITE /var/gerrit
ENV DOCKER_DIR /docker

RUN apt-get update && apt-get -y install sudo nfs-common
RUN mkdir -p $GERRIT_SITE/bin $GERRIT_SITE/etc $GERRIT_SITE/plugins \
    $USER_HOME/.ssh $GERRIT_SITE/git

COPY --from=artifacts /artifacts/ $GERRIT_SITE/

RUN useradd -m $GERRIT_USER \
  && touch $USER_HOME/.ssh/known_hosts \
  && chown -R $GERRIT_USER $USER_HOME \
  && chown -R $GERRIT_USER $GERRIT_SITE
RUN echo "$GERRIT_USER:$GERRIT_USER" | chpasswd && adduser "$GERRIT_USER" sudo

EXPOSE 29418 8080
USER $GERRIT_USER
ENTRYPOINT ["/docker/gerrit/gerrit_start.sh"]
