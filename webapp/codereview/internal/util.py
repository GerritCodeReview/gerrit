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

from google.appengine.ext import db
from codereview import models
from codereview.need_retry_pb2 import RetryRequestLaterResponse

class InternalAPI(object):
  """
    Marker superclass for service implementations which should be
    restricted to only role accounts executing batch processing.
  """
  def __init__(self):
    pass

def u(str):
  """Decode the UTF-8 byte sequence into a unicode object."""
  return str.decode('utf_8')

def commit_to_revision(proj, commit):
  """Converts a GitCommit into a RevisionId data store object.
  """
  p_a = commit.author
  p_c = commit.committer

  return models.RevisionId.get_or_insert_revision(
          project = proj,
          id = commit.id,
          ancestors = [p for p in commit.parent_id],
          message = db.Text(u(commit.message)),

          author_name = u(p_a.name),
          author_email = db.Email(u(p_a.email)),
          author_when = datetime.datetime.utcfromtimestamp(p_a.when),
          author_tz = p_a.tz,

          committer_name = u(p_c.name),
          committer_email = db.Email(u(p_c.email)),
          committer_when = datetime.datetime.utcfromtimestamp(p_c.when),
          committer_tz = p_a.tz,
          )

def automatic_retry(func):
  """Decorator that catches data store errors and sends a retry response."""
  def retry_wrapper(self, rpc_controller, req, done):
    try:
      func(self, rpc_controller, req, done)

    except db.InternalError:
      rsp = RetryRequestLaterResponse()
      done(rsp)

    except db.Timeout:
      rsp = RetryRequestLaterResponse()
      done(rsp)

    except db.TransactionFailedError:
      rsp = RetryRequestLaterResponse()
      done(rsp)

  return retry_wrapper

