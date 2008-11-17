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

from django import forms

from models import gql, Project, Branch, Account, Change
import git_models

from review_pb2 import *
from upload_bundle_pb2 import *
from internal.util import automatic_retry

class ReviewServiceImp(ReviewService):
  """
    Implements the review_service API for developer clients.
  """

  @automatic_retry
  def UploadBundle(self, rpc_controller, req, done):
    """
      Receives an uploaded Git style bundle and queues it for processing.
    """
    rsp = UploadBundleResponse()

    # can't get here if there isn't a current user
    current_user = users.get_current_user()
    
    if not Account.get_account_for_user(current_user).cla_verified:
      rsp.status_code = UploadBundleResponse.UNAUTHORIZED_USER
      done(rsp)
      return

    # Validate that we have an Account for everyone in reviewers
    invalid_reviewers = []
    reviewers = Account.get_accounts_for_emails([x for x in req.reviewers])
    for i in range(0, len(reviewers)):
      if not reviewers[i]:
        invalid_reviewers.append(req.reviewers[i])

    # Validate all of the email addresses
    invalid_cc = []
    for e in req.cc:
      if not forms.fields.email_re.search(e):
        invalid_cc.append(e)

    # Return failure if any of that was bad
    if invalid_reviewers or invalid_cc:
      rsp.status_code = UploadBundleResponse.UNKNOWN_EMAIL
      for e in invalid_reviewers:
        rsp.invalid_reviewers.append(e)
      for e in invalid_cc:
        rsp.invalid_cc.append(e)
      done(rsp)
      return

    reviewers = [x.user for x in reviewers]
    cc = [db.Email(x) for x in req.cc]

    if not req.dest_project:
      rsp.status_code = UploadBundleResponse.UNKNOWN_PROJECT
      done(rsp)
      return

    proj = req.dest_project
    if proj.endswith(".git"):
      proj = proj[0 : len(proj) - 4]

    proj = Project.get_project_for_name(proj)
    if not proj:
      rsp.status_code = UploadBundleResponse.UNKNOWN_PROJECT
      done(rsp)
      return

    if not req.dest_branch:
      rsp.status_code = UploadBundleResponse.UNKNOWN_BRANCH
      done(rsp)

    brch = Branch.get_branch_for_name(proj, req.dest_branch)
    if not brch:
      rsp.status_code = UploadBundleResponse.UNKNOWN_BRANCH
      done(rsp)
      return

    replaces = list()
    ids_to_check = list()
    for p in req.replace:
      id = int(p.change_id)
      ids_to_check.append(id)
      replaces.append('%d %s' % (id, p.object_id))

    if ids_to_check:
      for id, c in zip(ids_to_check, Change.get_by_id(ids_to_check)):
        if not c:
          rsp.status_code = UploadBundleResponse.UNKNOWN_CHANGE
          done(rsp)
          return
        if c.closed:
          rsp.status_code = UploadBundleResponse.CHANGE_CLOSED
          done(rsp)
          return

    rb = git_models.ReceivedBundle(
      dest_project = proj,
      dest_branch = brch,
      owner = current_user,
      state = git_models.ReceivedBundle.STATE_UPLOADING,
      reviewers = reviewers,
      cc = cc,
      contained_objects = list(req.contained_object),
      replaces = replaces)
    rb.put()

    rsp.bundle_id = str(rb.key().id())
    self._store_segment(req, rsp, rb, 1)
    done(rsp)

  @automatic_retry
  def ContinueBundle(self, rpc_controller, req, done):
    rsp = UploadBundleResponse()
    rsp.bundle_id = req.bundle_id

    rb = git_models.ReceivedBundle.get_by_id(int(req.bundle_id))
    if rb is None:
      rsp.status_code = UploadBundleResponse.UNKNOWN_BUNDLE
      done(rsp)
      return

    if rb.owner != users.get_current_user():
      rsp.status_code = UploadBundleResponse.NOT_BUNDLE_OWNER
      done(rsp)
      return

    self._store_segment(req, rsp, rb, req.segment_id)
    done(rsp)

  def _store_segment(self, req, rsp, rb, segment_id):
    rb.set_segment(segment_id, req.bundle_data)
    def trans():
      b = db.get(rb.key())
      if b.state != git_models.ReceivedBundle.STATE_UPLOADING:
        return False

      if b.n_segments < segment_id:
        b.n_segments = segment_id
      if not req.partial_upload:
        b.state = git_models.ReceivedBundle.STATE_NEW
      b.put()
      return True

    if db.run_in_transaction(trans):
      if req.partial_upload:
        rsp.status_code = UploadBundleResponse.CONTINUE
      else:
        rb.ready()
        rsp.status_code = UploadBundleResponse.RECEIVED
    else:
      rsp.status_code = UploadBundleResponse.BUNDLE_CLOSED

