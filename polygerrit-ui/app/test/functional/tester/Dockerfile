FROM selenium/standalone-chrome-debug

USER root

# nvm environment variables
ENV NVM_DIR /usr/local/nvm
ENV NODE_VERSION 9.4.0

# install nvm
# https://github.com/creationix/nvm#install-script
RUN wget -qO- https://raw.githubusercontent.com/creationix/nvm/v0.33.8/install.sh | bash

# install node and npm
RUN [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh" \
    && nvm install $NODE_VERSION \
    && nvm alias default $NODE_VERSION \
    && nvm use default

ENV NODE_PATH $NVM_DIR/v$NODE_VERSION/lib/node_modules
ENV PATH $NVM_DIR/versions/node/v$NODE_VERSION/bin:$PATH

RUN npm install -g jasmine
RUN npm install -g http-server

USER seluser

RUN mkdir -p /tmp/app
WORKDIR /tmp/app

RUN npm init -y
RUN npm install --save selenium-webdriver

EXPOSE 8080

COPY test-infra.js /tmp/app/node_modules
COPY run.sh /tmp/app/
COPY wait-for-server.sh /tmp/app/

#ENTRYPOINT [ "/tmp/app/run.sh" ]
