#!/usr/bin/python
#
# Copyright (C) 2021 The Android Open Source Project
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

# This script starts the local testsite in debug mode. If the flag "-u" is
# passed, Gerrit is built from the current state of the repository and the
# testsite is refreshed. The path to the testsite needs to be provided by
# the variable GERRIT_TESTSITE or as parameter (after any used flags).
# The testsite can be stopped by interrupting this script.

from pygerrit2 import GerritRestAPI, HTTPBasicAuth
from os import environ
from time import time

import logging
import requests

logging.basicConfig(level=logging.DEBUG)

if (not "GERRIT_USERNAME" in environ) or (not "GERRIT_PASSWORD" in environ) or (not "GERRIT_URL" in environ):
	print """ERROR: Unable to run without the following environment variables:
- GERRIT_USERNAME: Username to use for Gerrit
- GERRIT_PASSWORD: Password to use for Gerrit
- GERRIT_URL: Gerrit URL to use for REST-API"""
	exit(-1)

print "Accessing Gerrit Home Page at {}".format(environ["GERRIT_URL"])
requests.get(environ["GERRIT_URL"])

auth = HTTPBasicAuth(environ["GERRIT_USERNAME"], environ["GERRIT_PASSWORD"])
rest = GerritRestAPI(url=environ["GERRIT_URL"], auth=auth)

print "Running Python smoke-tests on {} as user {}".format(environ["GERRIT_URL"],environ["GERRIT_USERNAME"])

project = "test-repo-{}".format(int(time()*1000))
print "=> Create project {}".format(project)
rest.put("/projects/{}".format(project), json={"create_empty_commit":"true"})

print "=> Create a new change"
rest.post("/changes/", json={"project":project,"branch":"master","subject":"test change"})

print "=> Searching for the new change"
change = rest.get("/changes/?q=project:{}".format(project))[0]

print "=> Adding hashtag to new change {}".format(change["_number"])
rest.put("/changes/{}/topic".format(change["_number"]), json={"topic":"test-topic"})

print """
Python smoke-tests SUCCEEDED
"""
