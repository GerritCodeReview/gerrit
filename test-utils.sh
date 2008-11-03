#
# Copyright 2008 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

## 
## Source this script into your shell:
##
##   . test-utils.sh
##
## so that you can run:
##
##   gerrit clean
##   gerrit reset
##   gerrit web
##   gerrit mgr
##
## to setup a local testing environment using the Google App Engine
## SDK and a local Java installation.
##

TEST_DIR=test
TEST_CRM_DATA_DIR=$TEST_DIR/crm-data
TEST_GERRIT_DATASTORE_DIR=$TEST_DIR/gerrit-datastore

TEST_CLIENT0=$TEST_DIR/client0
TEST_CLIENT1=$TEST_DIR/client1
TEST_LOCAL_CONFIG=$TEST_DIR/localhost.config

TEST_CRM_PASSWORD_FILE=$TEST_DIR/.crm-password

function gerrit-clean()
{
  rm -rf $TEST_DIR
}

function gerrit-reset()
{
  FULL_GIT_BASE=`pwd`/$TEST_CRM_DATA_DIR

  # delete the old data
  gerrit-clean

  # make the crm data
  FULL_CRM_DATA_FILE=`pwd`/crm-data.tar.gz
  mkdir -p $TEST_DIR/crm-data
  ( cd $TEST_CRM_DATA_DIR ; tar zxf $FULL_CRM_DATA_FILE )

  # make two git clients (should use repo)
  mkdir -p $TEST_CLIENT0
  ( cd $TEST_CLIENT0 ; git clone $FULL_GIT_BASE/test.git > /dev/null)
  mkdir -p $TEST_CLIENT1
  ( cd $TEST_CLIENT1 ; git clone $FULL_GIT_BASE/test.git > /dev/null)

  make all

  # make localhost.config
  echo "[user]" > $TEST_LOCAL_CONFIG
  echo "  name = Gerrit Code Review" >> $TEST_LOCAL_CONFIG
  echo "  email = gerrit@localhost" >> $TEST_LOCAL_CONFIG
  echo "" >> $TEST_LOCAL_CONFIG
  echo "[codereview]" >> $TEST_LOCAL_CONFIG
  echo "  server = http://localhost:8080/" >> $TEST_LOCAL_CONFIG
  echo "  basedir = $(pwd)/$TEST_CRM_DATA_DIR"  >> $TEST_LOCAL_CONFIG
  echo "  username = android-git@google.com" >> $TEST_LOCAL_CONFIG
  echo "  secureconfig = .crm-password" \
      >> $TEST_LOCAL_CONFIG
  echo "  sleep = 10" >> $TEST_LOCAL_CONFIG
  echo "  threads = 1" >> $TEST_LOCAL_CONFIG

  echo
  echo "Finished.  Now you can run:"
  echo "   gerrit web   to run webapp/"
  echo "   gerrit mgr   to run mgrapp/"
  echo
}


# pack the git repository into a new crm-data.tar.gz
function gerrit-pack-crm-data()
{
  FULL_CRM_DATA_FILE=`pwd`/crm-data.tar.gz
  rm -f crm-data.tar.gz
  ( cd $TEST_CRM_DATA_DIR ; tar czf $FULL_CRM_DATA_FILE * )
}

# run webapp on google app engine dev server
function gerrit-web()
{
  FULL_GERRIT_DATASTORE_DIR=`pwd`/$TEST_GERRIT_DATASTORE_DIR
  make serve DATASTORE=$FULL_GERRIT_DATASTORE_DIR
}

# run mgrapp
function gerrit-mgr()
{
  if [ ! -f $TEST_CRM_PASSWORD_FILE ] ; then
    ( curl http://localhost:8080/dev_init > $TEST_CRM_PASSWORD_FILE )
    ( ./mgrapp/bin/mgr $TEST_LOCAL_CONFIG sync)
  fi
  ( ./mgrapp/bin/mgr $TEST_LOCAL_CONFIG )
}

function gerrit-help()
{
  echo "commands:"
  echo "  clean"
  echo "  reset"
  echo "  pack-crm-data"
  echo "  web"
  echo "  mgr"
  echo "  upload"
}

function gerrit-upload()
{
  python2.5 ../../../webapp/git_upload.py -s localhost:8080 \
    -e author@example.com -p test -b refs/heads/master -B HEAD^
}

# main gerrit command
function gerrit()
{
  if [ ! -f test-utils.sh ] ; then
    echo Run gerrit from the directory that contains test-utils.sh
    return
  fi

  case $1 in
    clean)
      gerrit-clean
      ;;
    reset)
      gerrit-reset
      ;;
    pack-crm-data)
      gerrit-pack-crm-data
      ;;
    web|gae)
      gerrit-web
      ;;
    mgr|crm)
      gerrit-mgr
      ;;
    help)
      gerrit-help
      ;;
    *)
      echo invalid gerrit command $1
      ;;
  esac
}


# vi: sts=2 ts=2 sw=2 nocindent
