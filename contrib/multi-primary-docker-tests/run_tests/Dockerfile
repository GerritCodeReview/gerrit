FROM debian:10

ARG UID=1000
ARG GID=1000
ENV USER gerrit_admin
ENV USER_EMAIL gerrit_admin@example.com
ENV USER_HOME /home/$USER
ENV USER_RUN_TEST_DIR /docker/run_tests

RUN apt-get update && apt-get install -y \
  curl git jq openssh-server uuid-runtime libltdl7 \
  && sed -i s/#PermitRootLogin.*/PermitRootLogin\ yes/ /etc/ssh/sshd_config

RUN groupadd -f -g $GID users2
RUN useradd -u $UID -g $GID $USER

RUN echo "KexAlgorithms +diffie-hellman-group1-sha1" >> /etc/ssh/ssh_config \
  && echo "Ciphers aes128-ctr,aes192-ctr,aes256-ctr,aes128-cbc,3des-cbc" >> /etc/ssh/ssh_config \
  && echo "StrictHostKeyChecking no" >> /etc/ssh/ssh_config

RUN mkdir -p $USER_HOME/workspace $USER_HOME/.ssh && chown -R $USER $USER_HOME

USER $USER
ENTRYPOINT ["tail", "-f", "/dev/null"]
