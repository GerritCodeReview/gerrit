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

from admin_pb2 import AdminService
from sync_project_pb2 import *
from util import u, InternalAPI

class AdminServiceImp(AdminService, InternalAPI):
  def SyncProject(self, rpc_controller, req, done):
    rsp = SyncProjectResponse()

    proj = models.Project.get_project_for_name(req.project_name)
    if proj is None:
      proj = models.Project(name = req.project_name)
      proj.put()

    really_exists = set()
    for bs in req.branch:
      branch = models.Branch.get_or_insert_branch(proj, bs.branch_name)
      really_exists.add(branch.name)

    to_delete = []
    for b in list(gql(models.Branch, 'WHERE project = :1', proj)):
      if b.name not in really_exists:
        to_delete += gql(models.BuildAttempt, 'WHERE branch = :1', b)

    if to_delete:
      db.delete(to_delete)

    done(rsp)
