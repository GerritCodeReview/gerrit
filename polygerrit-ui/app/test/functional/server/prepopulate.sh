#!/bin/sh -e

git config -f /var/gerrit/etc/gerrit.config sendemail.enable false
git config -f /var/gerrit/etc/gerrit.config gerrit.cdnPath http://infra:8081/

eval "$(ssh-agent -s)"
ssh-keygen -b 2048 -t rsa -f /tmp/sshkey -q -N ""
ssh-add /tmp/sshkey
SSH_KEY=`cat /tmp/sshkey.pub`

mkdir ~/.ssh
chmod 0700 ~/.ssh
echo -n "[localhost]:29418 " >> ~/.ssh/known_hosts
cat ~/etc/ssh_host_ecdsa_key.pub >> ~/.ssh/known_hosts

if [ "$UPGRADE_SERVER" ]; then
    echo "Waiting for gerrit.war"
    while [ ! -f /app/gerrit.war ]; do
        sleep 1
    done

    echo "Upgrading Gerrit"
    cp /app/gerrit.war /var/gerrit/bin
    java -jar /var/gerrit/bin/gerrit.war init --batch -d /var/gerrit
fi

/var/gerrit/bin/gerrit.sh start

echo Prepopulating...

# Get GerritAccount
curl -L --silent --output /dev/null --cookie-jar /tmp/cookies.txt http://localhost:8080/login/%23%2Fsettings%2Fssh-keys?account_id=1000000
# Get XSRF_TOKEN
curl -L --silent --output /dev/null --cookie-jar /tmp/cookies.txt http://localhost:8080/login/%23%2Fsettings%2Fssh-keys?account_id=1000000

XSRF_TOKEN=`cat /tmp/cookies.txt | grep XSRF_TOKEN | cut -f 7`

curl -L --silent --output /dev/null --cookie /tmp/cookies.txt -H "Content-Type: plain/text" -H "X-Gerrit-Auth: $XSRF_TOKEN" -X POST -d "$SSH_KEY" 'http://localhost:8080/accounts/self/sshkeys'

# Create additional user
USER1='{"name": "Jane Doe", "username": "jd", "email": "jd@gerritcodereview.com", "http_password": "secret", "groups": []}'
curl -L --silent --output /dev/null --cookie /tmp/cookies.txt -H "X-Gerrit-Auth: $XSRF_TOKEN" \
     -X PUT -H "Content-Type: application/json" -d "$USER1" \
     http://localhost:8080/accounts/jd

# Create a test project
curl -L --silent --output /dev/null --cookie /tmp/cookies.txt -H "X-Gerrit-Auth: $XSRF_TOKEN" \
     -X PUT -H "Content-Type: application/json" -d '{branches: ["master"], "create_empty_commit": true}' \
     http://localhost:8080/projects/testproject

# Create a test change
CHANGE1='{"project": "testproject", "subject": "Test change, please ignore", "branch": "master", "status": "NEW"}'
curl -L --silent --output /dev/null --cookie /tmp/cookies.txt -H "X-Gerrit-Auth: $XSRF_TOKEN" \
     -X POST -H "Content-Type: application/json" -d "$CHANGE1" \
     http://localhost:8080/changes/

# Use following for admin rest api access:
# curl -L --cookie /tmp/cookies.txt -H "X-Gerrit-Auth: $XSRF_TOKEN"

echo Prepopulation complete.

trap : TERM INT
sleep infinity & wait
