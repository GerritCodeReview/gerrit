# Copyright 2008 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import datetime
import logging

from google.appengine.ext import db

from codereview import models
from codereview.models import gql

from build_pb2 import BuildService
from submit_build_pb2 import *
from post_build_result_pb2 import *
from prune_builds_pb2 import *
from util import InternalAPI, automatic_retry

MAX_PRUNE = 100

class BuildServiceImp(BuildService, InternalAPI):
  @automatic_retry
  def SubmitBuild(self, rpc_controller, req, done):
    rsp = SubmitBuildResponse()

    branch = db.get(db.Key(req.branch_key))
    new_changes = [db.Key(c) for c in req.new_change]

    build = models.BuildAttempt(
              branch = branch,
              revision_id = req.revision_id,
              new_changes = new_changes)
    build.put()

    rsp.build_id = build.key().id()
    done(rsp)

  @automatic_retry
  def PostBuildResult(self, rpc_controller, req, done):
    rsp = PostBuildResultResponse()

    if req.build_status == PostBuildResultRequest.SUCCESS:
      ok = True
    elif req.build_status == PostBuildResultRequest.BUILD_FAILED:
      ok = False
    else:
      raise InvalidValueError, req.build_status

    build = models.BuildAttempt.get_by_id(req.build_id)
    if not build.finished:
      build.finished = True
      build.success = ok
      build.put()

    branch = build.branch
    project = branch.project

    rsp.dest_project_name = str(project.name)
    rsp.dest_project_key = str(project.key())

    rsp.dest_branch_name = str(branch.name)
    rsp.dest_branch_key = str(branch.key())

    rsp.revision_id = str(build.revision_id)
    for patchset_key in build.new_changes:
      rsp.new_change.append(str(patchset_key))

    done(rsp)

  @automatic_retry
  def PruneBuilds(self, rpc_controller, req, done):
    rsp = PruneBuildsResponse()

    build_list = []

    for m in [_AgedSuccess,
              _AgedFailed,
              ]:
      build_list.extend(m())
      if len(build_list) >= MAX_PRUNE:
        break

    if build_list:
      db.delete(build_list)
      rsp.status_code = PruneBuildsResponse.BUILDS_PRUNED
    else:
      rsp.status_code = PruneBuildsResponse.QUEUE_EMPTY
    done(rsp)

def _AgedSuccess():
  aged = datetime.datetime.now() - datetime.timedelta(days=2)
  return gql(models.BuildAttempt,
             'WHERE success = :1 AND started <= :2',
             True, aged).fetch(MAX_PRUNE)

def _AgedFailed():
  aged = datetime.datetime.now() - datetime.timedelta(days=7)
  return gql(models.BuildAttempt,
             'WHERE success = :1 AND started <= :2',
             False, aged).fetch(MAX_PRUNE)
