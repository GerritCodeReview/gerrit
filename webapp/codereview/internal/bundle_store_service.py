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

from codereview.models import gql
from codereview.git_models import *

from bundle_store_pb2 import BundleStoreService
from next_received_bundle_pb2 import *
from update_received_bundle_pb2 import *
from prune_bundles_pb2 import *
from util import InternalAPI, u, commit_to_revision, automatic_retry

MAX_PRUNE = 10
MAX_SEGS_PER_PRUNE = 10

class BundleStoreServiceImp(BundleStoreService, InternalAPI):
  @automatic_retry
  def NextReceivedBundle(self, rpc_controller, req, done):
    rsp = NextReceivedBundleResponse()

    rb = ReceivedBundle.lock_next_new()
    if rb:
      rsp.status_code = NextReceivedBundleResponse.BUNDLE_AVAILABLE
      rsp.bundle_key = str(rb.key())
      rsp.dest_project = str(rb.dest_project.name)
      rsp.dest_project_key = str(rb.dest_project.key())
      rsp.dest_branch_key = str(rb.dest_branch.key())
      rsp.owner = str(rb.owner.email())
      rsp.n_segments = rb.n_segments
      seg = rb.get_segment(1)
      if seg:
        rsp.bundle_data = seg.bundle_data
    else:
      rsp.status_code = NextReceivedBundleResponse.QUEUE_EMPTY
    done(rsp)

  @automatic_retry
  def BundleSegment(self, rpc_controller, req, done):
    rsp = BundleSegmentResponse()

    rb = db.get(db.Key(req.bundle_key))
    if not rb:
      rsp.status_code = BundleSegmentResponse.UNKNOWN_BUNDLE
      done(rsp)
      return

    seg = rb.get_segment(req.segment_id)
    if seg:
      rsp.status_code = BundleSegmentResponse.DATA
      rsp.bundle_data = seg.bundle_data
    else:
      rsp.status_code = BundleSegmentResponse.UNKNOWN_SEGMENT
    done(rsp)

  @automatic_retry
  def UpdateReceivedBundle(self, rpc_controller, req, done):
    rsp = UpdateReceivedBundleResponse()

    old_state = ReceivedBundle.STATE_UNPACKING
    sc = req.status_code
    if UpdateReceivedBundleRequest.UNPACKED_OK == sc:
      new_state = ReceivedBundle.STATE_UNPACKED
      err_msg = None
    elif UpdateReceivedBundleRequest.SUSPEND_BUNDLE == sc:
      new_state = ReceivedBundle.STATE_SUSPENDED
      err_msg = req.error_details
    else:
      new_state = ReceivedBundle.STATE_INVALID
      err_msg = req.error_details

    try:
      ReceivedBundle.update_state(req.bundle_key, old_state, new_state, err_msg)
      rsp.status_code = UpdateReceivedBundleResponse.UPDATED
    except InvalidBundleId, err:
      logging.warn("Invalid bundle id %s: %s" % (req.bundle_key, err))
      rsp.status_code = UpdateReceivedBundleResponse.INVALID_BUNDLE
    except InvalidBundleState, err:
      logging.warn("Invalid bundle state %s: %s" % (req.bundle_key, err))
      rsp.status_code = UpdateReceivedBundleResponse.INVALID_STATE
    done(rsp)

  @automatic_retry
  def PruneBundles(self, rpc_controller, req, done):
    rsp = PruneBundlesResponse()

    rb_list = []
    to_rm = []

    for m in [_AgedUploading,
              _AgedInvalid,
              _AgedSuspended,
              _AgedUnpacking,
              _AgedUnpacked,
              ]:
      rb_list.extend(m())
      if len(rb_list) >= MAX_PRUNE:
        break

    for rb in rb_list:
      segs = gql(ReceivedBundleSegment,
                'WHERE ANCESTOR IS :1',
                rb).fetch(MAX_SEGS_PER_PRUNE)
      if len(segs) < MAX_SEGS_PER_PRUNE:
        to_rm.append(rb)
      to_rm.extend(segs)

    if to_rm:
      db.delete(to_rm)
      rsp.status_code = PruneBundlesResponse.BUNDLES_PRUNED
    else:
      rsp.status_code = PruneBundlesResponse.QUEUE_EMPTY
    done(rsp)

def _AgedUploading():
  aged = datetime.datetime.now() - datetime.timedelta(days=7)
  return gql(ReceivedBundle,
             'WHERE state = :1 AND created <= :2',
             ReceivedBundle.STATE_UPLOADING, aged).fetch(MAX_PRUNE)

def _AgedInvalid():
  aged = datetime.datetime.now() - datetime.timedelta(days=2)
  return gql(ReceivedBundle,
             'WHERE state = :1 AND created <= :2',
             ReceivedBundle.STATE_INVALID, aged).fetch(MAX_PRUNE)

def _AgedSuspended():
  aged = datetime.datetime.now() - datetime.timedelta(days=7)
  return gql(ReceivedBundle,
             'WHERE state = :1 AND created <= :2',
             ReceivedBundle.STATE_SUSPENDED, aged).fetch(MAX_PRUNE)

def _AgedUnpacking():
  aged = datetime.datetime.now() - datetime.timedelta(days=7)
  return gql(ReceivedBundle,
             'WHERE state = :1 AND created <= :2',
             ReceivedBundle.STATE_UNPACKING, aged).fetch(MAX_PRUNE)

def _AgedUnpacked():
  aged = datetime.datetime.now() - datetime.timedelta(hours=1)
  return gql(ReceivedBundle,
             'WHERE state = :1 AND created <= :2',
             ReceivedBundle.STATE_UNPACKED, aged).fetch(MAX_PRUNE)
