FROM gerritcodereview/gerrit
USER root

RUN yum -y install mkstemp openssh-server openssh-clients

EXPOSE 29418 8080

USER gerrit

ADD ./prepopulate.sh /tmp/

RUN /tmp/prepopulate.sh
