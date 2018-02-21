#!/bin/sh

eval "$(ssh-agent -s)"
ssh-keygen -b 2048 -t rsa -f /tmp/sshkey -q -N ""
ssh-add /tmp/sshkey
SSH_KEY=`cat /tmp/sshkey.pub`

mkdir ~/.ssh
chmod 0700 ~/.ssh
echo -n "[localhost]:29418 " >> ~/.ssh/known_hosts
cat ~/etc/ssh_host_ecdsa_key.pub >> ~/.ssh/known_hosts

git config -f /var/gerrit/etc/gerrit.config sendemail.enable false

~/bin/gerrit.sh run &

sleep 25

# Get GerritAccount
curl -L --silent --output /dev/null --cookie-jar /tmp/cookies.txt http://localhost:8080/login/%23%2Fsettings%2Fssh-keys?account_id=1000000
# Get XSRF_TOKEN
curl -L --silent --output /dev/null --cookie-jar /tmp/cookies.txt http://localhost:8080/login/%23%2Fsettings%2Fssh-keys?account_id=1000000

XSRF_TOKEN=`cat /tmp/cookies.txt | grep XSRF_TOKEN | cut -f 7`

curl -L --silent --output /dev/null --cookie /tmp/cookies.txt -H "Content-Type: plain/text" -H "X-Gerrit-Auth: $XSRF_TOKEN" -X POST -d "$SSH_KEY" 'http://localhost:8080/accounts/self/sshkeys'

ssh -tt -p 29418 admin@localhost

exit 0

# Hi Administrator, you have successfully connected over SSH.
# Use following with full access over SSH:
# curl -L --cookie /tmp/cookies.txt -H "X-Gerrit-Auth: $XSRF_TOKEN"
