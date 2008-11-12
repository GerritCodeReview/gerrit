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

from google.appengine.api import users
from google.appengine.ext import db

from codereview import models

from change_pb2 import ChangeService
from add_patchset_pb2 import *
from complete_patchset_pb2 import *
from upload_patchset_file_pb2 import *
from submit_change_pb2 import *
from util import *

class ChangeServiceImp(ChangeService, InternalAPI):

  @automatic_retry
  def SubmitChange(self, rpc_controller, req, done):
    rsp = SubmitChangeResponse()

    user = users.User(req.owner)
    branch = db.get(db.Key(req.dest_branch_key))
    if not branch:
      rsp.status_code = SubmitChangeResponse.UNKNOWN_BRANCH
      done(rsp)

    rev = commit_to_revision(branch.project, req.commit)
    if rev.patchset:
      rsp.status_code = SubmitChangeResponse.PATCHSET_EXISTS
      done(rsp)

    subject = u(req.commit.subject)[0:100]

    def trans():
      change = models.Change(
                 subject = subject,
                 description = u(req.commit.message),
                 owner = user,
                 dest_project = branch.project,
                 dest_branch = branch,
                 n_patchsets = 1)
      change.put()

      patchset = models.PatchSet(
                   change = change,
                   owner = user,
                   parent = change,
                   revision = rev,
                   id = 1)
      patchset.put()
      return (change, patchset)
    change, patchset = db.run_in_transaction(trans)

    if rev.link_patchset(patchset):
      rsp.status_code = SubmitChangeResponse.CREATED
      rsp.change_id = change.key().id()
      rsp.patchset_id = patchset.id
      rsp.patchset_key = str(patchset.key())
    else:
      db.delete([change, patchset])
      rsp.status_code = SubmitChangeResponse.PATCHSET_EXISTS
    done(rsp)

  @automatic_retry
  def AddPatchSet(self, rpc_controller, req, done):
    rsp = AddPatchSetResponse()

    user = users.User(req.owner)
    branch = db.get(db.Key(req.dest_branch_key))
    if not branch:
      rsp.status_code = AddPatchSetResponse.UNKNOWN_BRANCH
      done(rsp)

    rev = commit_to_revision(branch.project, req.commit)
    if rev.patchset:
      rsp.status_code = AddPatchSetResponse.PATCHSET_EXISTS
      done(rsp)

    subject = u(req.commit.subject)[0:100]

    def trans():
      change = models.Change.get_by_id(req.change_id)
      if not change:
        return None

      change.subject = subject
      change.description = u(req.commit.message)
      change.n_patchsets += 1
      id = change.n_patchsets
      change.put()

      patchset = models.PatchSet(
                   change = change,
                   owner = user,
                   parent = change,
                   revision = rev,
                   id = id)
      patchset.put()
      return patchset
    patchset = db.run_in_transaction(trans)
    if not patchset:
      rsp.status_code = AddPatchSetResponse.UNKNOWN_CHANGE
      done(rsp)

    if rev.link_patchset(patchset):
      rsp.status_code = AddPatchSetResponse.CREATED
      rsp.patchset_id = patchset.id
      rsp.patchset_key = str(patchset.key())
    else:
      db.delete(patchset)
      rsp.status_code = AddPatchSetResponse.PATCHSET_EXISTS
    done(rsp)

  @automatic_retry
  def UploadPatchsetFile(self, rpc_controller, req, done):
    rsp = UploadPatchsetFileResponse()

    patchset = db.get(db.Key(req.patchset_key))
    if not patchset:
      rsp.status_code = UploadPatchsetFileResponse.UNKNOWN_PATCHSET
      done(rsp)
      return

    if patchset.complete or patchset.change.closed:
      rsp.status_code = UploadPatchsetFileResponse.CLOSED
      done(rsp)
      return

    if UploadPatchsetFileRequest.ADD == req.status:
      status = 'A'
    elif UploadPatchsetFileRequest.MODIFY == req.status:
      status = 'M'
    elif UploadPatchsetFileRequest.DELETE == req.status:
      status = 'D'
    else:
      status = '?'

    try:
      if req.base_id:
        old_data = models.DeltaContent.create_content(
                     id = req.base_id,
                     text_z = req.base_z)
        new_data = models.DeltaContent.create_content(
                     id = req.final_id,
                     text_z = req.patch_z,
                     base = old_data)
        if new_data.text_z == req.patch_z:
          diff_data = new_data
        else:
          diff_data = models.DeltaContent.create_patch(
                        id = req.patch_id,
                        text_z = req.patch_z)
      else:
        old_data = None
        new_data = None
        diff_data = models.DeltaContent.create_patch(
                      id = req.patch_id,
                      text_z = req.patch_z)
    except models.DeltaPatchingException:
      logging.error("Patch error on change %s, patch set %s, file %s"
        % (patchset.change.key().id(),
           str(patchset.id),
           u(req.file_name))
      )
      rsp.status_code = UploadPatchsetFileResponse.PATCHING_ERROR
      done(rsp)
      return

    patch = models.Patch.get_or_insert_patch(
              patchset = patchset,
              filename = u(req.file_name),
              status = status,
              multi_way_diff = req.multi_way_diff,
              n_comments = 0,
              old_data = old_data,
              new_data = new_data,
              diff_data = diff_data)

    if old_data:
      models.CachedDeltaContent.get(old_data.key())
      models.CachedDeltaContent.get(new_data.key())
      if diff_data != new_data:
        models.CachedDeltaContent.get(diff_data.key())
    else:
      models.CachedDeltaContent.get(diff_data.key())

    rsp.status_code = UploadPatchsetFileResponse.CREATED
    done(rsp)

  @automatic_retry
  def CompletePatchset(serlf, rpc_controller, req, done):
    rsp = CompletePatchsetResponse()

    patchset = db.get(db.Key(req.patchset_key))
    if not patchset.complete:
      patchset.complete = True
      patchset.put()

      if len(req.compressed_filenames) > 0:
        models.PatchSetFilenames.store_compressed(
          patchset,
          req.compressed_filenames)
    done(rsp)
