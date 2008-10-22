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

import logging

from google.appengine.ext import db

from codereview import models
from codereview.models import gql

from merge_pb2 import MergeService
from pending_merge_pb2 import *
from post_merge_result_pb2 import *
from post_branch_update_pb2 import *
from util import InternalAPI, automatic_retry

from codereview import email

def _send_clean_merge_email(http_request, change):
  if not change.emailed_clean_merge:
    msg = email.send_change_message(http_request, change,
                              "mails/clean_merge.txt", None, None)
    change.emailed_clean_merge = True
    return msg
  return None
  
def _send_missing_dependency_merge_email(http_request, change):
  if not change.emailed_clean_merge:
    msg = email.send_change_message(http_request, change,
                              "mails/missing_dependency.txt", None, None)
    change.emailed_missing_dependency = True
    return msg
  return None

def _send_path_conflict_email(http_request, change):
  if not change.emailed_clean_merge:
    msg = email.send_change_message(http_request, change,
                              "mails/path_conflict.txt", None, None)
    change.emailed_path_conflict = True
    return msg
  return None

class InvalidBranchStatusError(Exception):
  """The branch cannot be updated in this way at this time."""

class MergeServiceImp(MergeService, InternalAPI):
  @automatic_retry
  def NextPendingMerge(self, rpc_controller, req, done):

    patchsets = []
    while not patchsets:
      branch = gql(models.Branch,
                  "WHERE status = 'NEEDS_MERGE'"
                  " ORDER BY merge_submitted").get()
      if branch is None:
        break
      patchsets = branch.begin_merge()

    rsp = PendingMergeResponse()
    if patchsets:
      first = patchsets[0].change
      rsp.status_code = PendingMergeResponse.MERGE_READY
      rsp.dest_project_name = str(first.dest_project.name)
      rsp.dest_project_key = str(first.dest_project.key())
      rsp.dest_branch_name = str(first.dest_branch.name)
      rsp.dest_branch_key = str(first.dest_branch.key())
      for ps in patchsets:
        pmi = rsp.change.add()
        pmi.patchset_key = str(ps.key())
        pmi.revision_id = str(ps.revision.id)
    else:
      rsp.status_code = PendingMergeResponse.QUEUE_EMPTY
    done(rsp)

  @automatic_retry
  def PostMergeResult(self, rpc_controller, req, done):
    rsp = PostMergeResultResponse()

    success = []
    fail = []
    defer = []

    for ri in req.change:
      sc = ri.status_code
      ps = db.get(db.Key(ri.patchset_key))

      if ps.change.merged:
        success.append(ps)
        continue

      def chg_trans(key):
        change = db.get(key)
        if change.merge_patchset.key() != ps.key():
          return False

        if sc == MergeResultItem.CLEAN_MERGE:
          pass

        elif sc == MergeResultItem.ALREADY_MERGED:
          change.merged = True
          change.closed = True
          change.put()

        elif sc == MergeResultItem.MISSING_DEPENDENCY:
          pass

        elif sc == MergeResultItem.PATH_CONFLICT:
          change.unsubmit_merge()
          change.put()

        return True

      if db.run_in_transaction(chg_trans, ps.change.key()):
        if sc == MergeResultItem.CLEAN_MERGE:
          msg = _send_clean_merge_email(self.http_request, ps.change)
          ps.change.put()
          if msg:
            msg.put()

        elif sc == MergeResultItem.ALREADY_MERGED:
          success.append(ps)

        elif sc == MergeResultItem.MISSING_DEPENDENCY:
          msg = _send_missing_dependency_merge_email(self.http_request,
                                                     ps.change)
          ps.change.put()
          if msg:
            msg.put()
          defer.append(ps)

        elif sc == MergeResultItem.PATH_CONFLICT:
          msg = _send_path_conflict_email(self.http_request, ps.change)
          ps.change.put()
          if msg:
            msg.put()
          fail.append(ps)
      else:
        fail.append(ps)

    branch = db.get(db.Key(req.dest_branch_key))
    branch.finish_merge(success, fail, defer)

    done(rsp)

  @automatic_retry
  def PostBranchUpdate(self, rpc_controller, req, done):
    rsp = PostBranchUpdateResponse()

    branch = db.get(db.Key(req.branch_key))
    merged = [db.get(db.Key(c_key)) for c_key in req.new_change]

    branch.merged(merged)

    for ps in merged:
      def trans(key):
        change = db.get(key)
        if change.merge_patchset.key() == ps.key():
          change.merged = True
          change.closed = True
          change.put()
      db.run_in_transaction(trans, ps.change.key())

    done(rsp)
