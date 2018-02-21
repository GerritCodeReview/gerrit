FROM gerritcodereview/gerrit

USER root

RUN yum -y install mkstemp openssh-server openssh-clients

EXPOSE 29418 8080

VOLUME ["/var/gerrit/git", "/var/gerrit/index", "/var/gerrit/cache", "/var/gerrit/db", "/var/gerrit/etc"]

USER gerrit

ADD ./prepopulate.sh /tmp/

CMD /tmp/prepopulate.sh
