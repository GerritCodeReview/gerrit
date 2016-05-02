#!/usr/bin/env python
# Copyright (C) 2016 The Android Open Source Project
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

# This scipt will populate an emtpy standard Gerrit instance with some
# data for local testing.
# TODO: Make real git commits instead of empty changes
# TODO: Add comments

import sys
import json
import requests
import requests.auth
import os
import shutil
import Crypto.PublicKey
import random

TMP_PATH = "./tmp"
BASE_URL = 'http://localhost:8080/a/'
ACCESS_URL = BASE_URL + 'access/'
ACCOUNTS_URL = BASE_URL + 'accounts/'
CHANGES_URL = BASE_URL + 'changes/'
CONFIG_URL = BASE_URL + 'config/'
GROUPS_URL = BASE_URL + 'groups/'
PLUGINS_URL = BASE_URL + 'plugins/'
PROJECTS_URL = BASE_URL + 'projects/'

ADMIN_DIGEST = requests.auth.HTTPDigestAuth('admin',
                                            'secret')

GROUP_ADMIN = {}

HEADERS = {'Content-Type': 'application/json', 'charset': 'UTF-8'}


def clean(jsonString):
  # Strip JSON XSS Tag
  if jsonString.startsWith("\n)]}'"):
    return jsonString[5:]
  return jsonString


def digestAuth(user):
  return requests.auth.HTTPDigestAuth(user['username'],
                                      user['http_password'])


def fetchAdminGroup():
  global GROUP_ADMIN
  # Get admin group
  r = json.loads(clean(requests.get(GROUPS_URL + '?suggest=ad&p=All-Projects',
                                    headers=HEADERS,
                                    auth=ADMIN_DIGEST).text))
  adminGroupName = r.keys()[0]
  GROUP_ADMIN = r[adminGroupName]
  GROUP_ADMIN['name'] = adminGroupName


def generateRandomText():
  return ' '.join([random.choice('lorem ipsum '
                                 'doleret delendam '
                                 '\n esse'.split(' ')) for x in xrange(1, 100)])


def setUp():
  os.makedirs(TMP_PATH + "/ssh")
  os.makedirs(TMP_PATH + "/repos")
  fetchAdminGroup()


def loadRandomUserNames(numUsers):
  # Using an open-source provider for random names
  # See https://randomuser.me/documentation or https://github.com/randomapi
  url = "http://api.randomuser.me/?results=" + `numUsers`
  data = json.loads(requests.get(url).text)
  return [{'firstname': x["name"]["first"],
           'lastname': x["name"]["last"],
           'name': x["name"]["first"] + ' ' + x["name"]["last"],
           'username': x["login"]["username"],
           'email': x["email"],
           'http_password': x["login"]["password"],
           'pictureURL': x["picture"]["thumbnail"],
           'groups': []
           }
          for x in data["results"]]


def generateSSHKeys(gerritUsers):
  for user in gerritUsers:
    key = Crypto.PublicKey.RSA.generate(2048)
    with open("./tmp/ssh/" + user["username"] + "private.key", 'w') \
        as content_file:
      os.chmod("./tmp/ssh/" + user["username"] + "private.key", 0600)
      content_file.write(key.exportKey('PEM'))
    user["private_ssh_key"] = key.exportKey('PEM')
    pubkey = key.publickey()
    with open("./tmp/ssh/" + user["username"] + "public.key", 'w') \
        as content_file:
      content_file.write(pubkey.exportKey('OpenSSH'))
    user["ssh_key"] = pubkey.exportKey('OpenSSH')


def createGerritGroups():
  groups = [
    {"name": "iOS-Maintainers", "description": "iOS Maintainers",
     "visible_to_all": True, "owner": GROUP_ADMIN['name'],
     "owner_id": GROUP_ADMIN['id']},
    {"name": "Android-Maintainers", "description": "Android Maintainers",
     "visible_to_all": True, "owner": GROUP_ADMIN['name'],
     "owner_id": GROUP_ADMIN['id']},
    {"name": "Backend-Maintainers", "description": "Backend Maintainers",
     "visible_to_all": True, "owner": GROUP_ADMIN['name'],
     "owner_id": GROUP_ADMIN['id']},
    {"name": "Script-Maintainers", "description": "Script Maintainers",
     "visible_to_all": True, "owner": GROUP_ADMIN['name'],
     "owner_id": GROUP_ADMIN['id']},
    {"name": "Security-Team", "description": "Sec Team",
     "visible_to_all": False, "owner": GROUP_ADMIN['name'],
     "owner_id": GROUP_ADMIN['id']}]
  for g in groups:
    requests.put(GROUPS_URL + g['name'],
                 json.dumps(g),
                 headers=HEADERS,
                 auth=ADMIN_DIGEST)
  return [g['name'] for g in groups]


def createGerritProjects(ownerGroups):
  projects = [
    {"id": "android", "name": "Android", "parent": "All-Projects",
     'branches': ['master'], "description": "Our android app.",
     'owners': [ownerGroups[0]], 'create_empty_commit': True},
    {"id": "ios", "name": "iOS", "parent": "All-Projects",
     'branches': ['master'], "description": "Our ios app.",
     'owners': [ownerGroups[1]], 'create_empty_commit': True},
    {"id": "backend", "name": "Backend", "parent": "All-Projects",
     'branches': ['master'], "description": "Our awesome backend.",
     'owners': [ownerGroups[2]], 'create_empty_commit': True},
    {"id": "scripts", "name": "Scripts", "parent": "All-Projects",
     'branches': ['master'], "description": "some small scripts.",
     'owners': [ownerGroups[3]], 'create_empty_commit': True}]
  for p in projects:
    requests.put(PROJECTS_URL + p['name'],
                 json.dumps(p),
                 headers=HEADERS,
                 auth=ADMIN_DIGEST)
  return [p['name'] for p in projects]


def createGerritUsers(gerritUsers):
  for user in gerritUsers:
    requests.put(ACCOUNTS_URL + user['username'],
                 json.dumps(user),
                 headers=HEADERS,
                 auth=ADMIN_DIGEST)


def cloneRepo():
  print 'TODO'


def createChange(user, projectName):
  randomCommitMessage = generateRandomText()
  change = {
    "project": projectName,
    "subject": randomCommitMessage.split('\n')[0],
    "branch": "master",
    "status": "NEW"
  }
  requests.post(CHANGES_URL,
                json.dumps(change),
                headers=HEADERS,
                auth=digestAuth(user))


def cleanUp():
  shutil.rmtree(TMP_PATH)


def main():
  setUp()
  gerritUsers = loadRandomUserNames(100)

  groupNames = createGerritGroups()
  for idx, u in enumerate(gerritUsers):
    u['groups'].append(groupNames[4 * idx / len(gerritUsers)])
    if (idx % 5 == 0):
      # Also add to security group
      u['groups'].append(groupNames[4])

  generateSSHKeys(gerritUsers)
  createGerritUsers(gerritUsers)

  projectNames = createGerritProjects(groupNames)

  for idx, u in enumerate(gerritUsers):
    createChange(u, projectNames[4 * idx / len(gerritUsers)])

  cleanUp()


try:
  main()
except:
  print "Cleaning up after exceptions"
  cleanUp()
  print "Unexpected error:", sys.exc_info()[0]
  raise
