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

"""Git specific data model (schema) for Gerrit."""

# Python imports
import hashlib
import logging
import re

# AppEngine imports
from google.appengine.ext import db
from google.appengine.api import users

# Gerrit imports
from models import gql
import models
import memcache

### Exceptions ###

class InvalidBundleId(Exception):
  """Bundle does not exist in data store."""

class InvalidBundleState(Exception):
  """Bundle has different state than expected."""


### Bundles ###

class ReceivedBundleSegment(models.BackedUpModel):
  """Binary segment of a submitted bundle."""

  # parent == ReceivedBundle
  segment_id = db.IntegerProperty(required=True)  # == key
  bundle_data = db.BlobProperty()


class ReceivedBundle(models.BackedUpModel):
  """A Git bundle submitted for review."""

  _NewIsEmpty = memcache.Key("ReceivedBundle.NewIsEmpty")

  STATE_UPLOADING = "UPLOADING"
  STATE_NEW = "NEW"
  STATE_UNPACKING = "UNPACKING"
  STATE_UNPACKED = "UNPACKED"
  STATE_INVALID = "INVALID"
  STATE_SUSPENDED = "SUSPENDED"

  state = db.StringProperty(required=True, default=STATE_UPLOADING,
                            choices=(STATE_UPLOADING,
                                     STATE_NEW,
                                     STATE_UNPACKING,
                                     STATE_UNPACKED,
                                     STATE_INVALID,
                                     STATE_SUSPENDED))
  invalid_details = db.TextProperty()

  # Where does this bundle merge to?
  dest_project = db.ReferenceProperty(models.Project, required=True)
  dest_branch = db.ReferenceProperty(models.Branch, required=True)

  # Who submitted this bundle, and when.
  owner = db.UserProperty(required=True)
  created = db.DateTimeProperty(required=True, auto_now_add=True)
  modified = db.DateTimeProperty(required=True, auto_now=True)

  # How much bundle is there?
  n_segments = db.IntegerProperty(required=True, default=0)
  contained_objects = db.StringListProperty()

  @classmethod
  def lock_next_new(cls):
    if ReceivedBundle._NewIsEmpty.get() == 1:
      return None

    try:
      rb = cls._lock_next_new_imp()
    except Timeout:
      return None
    except TransactionFailedError:
      return None

    if rb is None:
      ReceivedBundle._NewIsEmpty.set(1)
    return rb

  def ready(self):
      ReceivedBundle._NewIsEmpty.clear()

  @classmethod
  def _lock_next_new_imp(cls):
    for attempt in xrange(5):
      ro_rb = gql(cls, "WHERE state = :1 ORDER BY created",
                  ReceivedBundle.STATE_NEW).get()
      if ro_rb is None:
        return None

      def trans(key):
        rb = db.get(key)
        if rb.state == ReceivedBundle.STATE_NEW:
          rb.state = ReceivedBundle.STATE_UNPACKING
          rb.put()
          return True
        return False
      if db.run_in_transaction(trans, ro_rb.key()):
        return ro_rb
    return None

  @classmethod
  def update_state(cls, keystr, old_state, new_state, err_msg):
    key = db.Key(keystr)
    if key.kind() != cls.__name__:
        raise InvalidBundleId, keystr
    def trans():
      rb = db.get(key)
      if rb is None:
        raise InvalidBundleId, keystr
      if rb.state != old_state:
        raise InvalidBundleState, "%s != %s" % (rb.state, old_state)
      rb.state = new_state
      rb.invalid_details = err_msg
      rb.put()
    db.run_in_transaction(trans)

  def set_segment(self, segment_id, data):
    key = 's%d' % segment_id
    ReceivedBundleSegment.get_or_insert(
      key,
      segment_id = segment_id,
      bundle_data = db.Blob(data),
      parent = self)

  def get_segment(self, segment_id):
    key = 's%d' % segment_id
    return ReceivedBundleSegment.get_by_key_name(key, parent = self)
