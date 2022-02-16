#!/bin/bash

# To get this script to work you need to setup netrc for the main Gerrit instance
# and add your own ssh key to the main and mirror host. Also Gatling does not work with too new
# Java-versions. Java 8 and Java 11 works at least.

set_as_known_host()
{
  hostname=$1
  grep -q $hostname ~/.ssh/known_hosts || ssh-keyscan -t rsa -p 29418 $hostname >> ~/.ssh/known_hosts
}

set_account_id()
{
  hostname=$1
  account_name=$2
  result=$(curl --netrc -H "Content-Type: application/json" -X GET https://$hostname/gerrit/a/accounts/?q=name:$account_name | tail -n 1)
  if [ "$result" = "Unauthorized" ]; then
    echo "Unauthorized: setup netrc"
    exit 1
  fi
  account_id=$(echo $result | jq -r '.[0]._account_id')
}

package_not_installed()
{
  package=$1
  ! dpkg-query -W -f='${Status}' $package 2>/dev/null | grep -q "ok installed"
}

test_only() {
   mkdir -p output
   test_name=$1
   sbt "gatling:testOnly com.google.gerrit.scenarios.$test_name" >> output/${test_name}_output
}

if package_not_installed sbt; then
  echo "sbt not installed"
  read -p "Do you want to install sbt? (y/n)" yn
  case $yn in
    [yY]) sudo apt-get install sbt ;;
    [nN]) echo "sbt is required, will not continue"
      exit 0 ;;
    *) echo "invalid response"
      exit 1 ;;
  esac
fi

main="gittools-staging.se.axis.com"
mirror="git-staging-mirror01.se.axis.com"

while getopts 'hm:r:' flag; do
  case "${flag}" in
    h) echo "use flag -m to set main and flag -r to set mirror"
      exit 0 ;;
    m) main="${OPTARG}" ;;
    r) mirror="${OPTARG}" ;;
  esac
done

account_name="gerrit-e2e-test-user"
set_account_id $main $account_name
if [ "$account_id" = "null" ]; then
  echo "No e2e account found, create it instead"
  ssh -p 29418 $main gerrit create-account --group "'Administrators'" $account_name
  set_account_id $main $account_name
fi

mkdir -p /tmp/ssh-keys
ssh-keygen -m PEM -t rsa -C "gerrit-e2e-test-user@mail.com" -f /tmp/ssh-keys/id_rsa_test -N ''
set_as_known_host $main
set_as_known_host $mirror
cat /tmp/ssh-keys/id_rsa_test.pub | ssh -p 29418 $main gerrit set-account --add-ssh-key - $account_name
ssh -p 29418 $mirror gerrit flush-caches --cache sshkeys

result=$(curl --netrc -d '{"generate":true}' -H "Content-Type: application/json" -X PUT "https://$main/gerrit/a/accounts/$account_id/password.http")
http_password=${result:6:-1}
export JAVA_OPTS="-Dcom.google.gerrit.scenarios.hostname=$main \
                  -Dcom.google.gerrit.scenarios.ssh_port=29418 \
                  -Dcom.google.gerrit.scenarios.http_port=443 \
                  -Dcom.google.gerrit.scenarios.http_port1=443 \
                  -Dcom.google.gerrit.scenarios.http_scheme=https \
                  -Dcom.google.gerrit.scenarios.parent=e2e-Projects \
                  -Dcom.google.gerrit.scenarios.username=$account_name \
                  -Dcom.google.gerrit.scenarios.replica_hostname=$mirror \
                  -Dcom.google.gerrit.scenarios.context_path=/gerrit \
                  -Dcom.google.gerrit.scenarios.project_prefix=users/gerrit-e2e-test-user/ \
                  -Dcom.google.gerrit.scenarios.authenticated=true"
export GIT_HTTP_USERNAME=$account_name
export GIT_HTTP_PASSWORD=$http_password
export GIT_SSH_PRIVATE_KEY_PATH="/tmp/ssh-keys/id_rsa_test"

test_only AbandonChange
test_only CloneUsingBothProtocols
test_only CreateBranch
test_only CreateChange
test_only CreateProject
test_only GetMasterBranchRevision
test_only DeleteProject
test_only FlushProjectsCacheThenRebuild
test_only GetProjectsCacheEntries
test_only ListProjects
test_only ReplayRecordsFromFeeder
test_only RestoreChange
test_only SubmitChange
test_only SubmitChangeInBranch
test_only CheckNewProjectReplica1
test_only FlushProjectsCache
