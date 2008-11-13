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

import base64
import logging

from google.appengine.ext import db

import models
from backup_pb2 import *
from internal.util import automatic_retry

class GetLostError(Exception):
  pass

KINDS = {
  "ApprovalRight": models.ApprovalRight,
  "Project": models.Project,
  "Branch": models.Branch,
  "RevisionId": models.RevisionId,
  "BuildAttempt": models.BuildAttempt,
  "Change": models.Change,
  "PatchSetFilenames": models.PatchSetFilenames,
  "PatchSet": models.PatchSet,
  "Message": models.Message,
  "DeltaContent": models.DeltaContent,
  "Patch": models.Patch,
  "Comment": models.Comment,
  "Bucket": models.Bucket,
  "ReviewStatus": models.ReviewStatus,
  "Account": models.Account,
  "AccountGroup": models.AccountGroup,
}

class BackupServiceImp(BackupService):
  @automatic_retry
  def NextChunk(self, rpc_controller, req, done):
    if not self.http_request.user_is_admin:
      raise GetLostError()

    rsp = NextChunkResponse()

    try:
      cls = KINDS[req.kind]
    except KeyError:
      done(rsp)
      return

    sz = 20
    for o in models.gql(cls,
                        'WHERE last_backed_up <= :1',
                        req.last_backed_up).fetch(sz):
      e = rsp.entity.add()
      if o.key().id() is not None:
        e.key_id = o.key().id()
      if o.key().name() is not None:
        e.key_name = o.key().name()
      e.key = str(o.key())
      e.last_backed_up = o.last_backed_up

      if isinstance(o, models.DeltaContent):
        o.text_z = base64.b64encode(o.text_z)

      e.xml = o.to_xml().encode('utf_8')
    done(rsp)

  @automatic_retry
  def AckChunk(self, rpc_controller, req, done):
    if not self.http_request.user_is_admin:
      raise GetLostError()

    for i in req.entity:
      def t():
        o = db.get(i.key)
        if o.last_backed_up == i.last_backed_up:
          o.last_backed_up = req.last_backed_up
          o.put()
      db.run_in_transaction(t)

    rsp = AckChunkResponse()
    done(rsp)
