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
import hashlib
import hmac
import logging
import time
import zlib

from google.appengine.api import users
from google.appengine.ext import webapp
from google.appengine.ext.webapp import util

from django.http import HttpResponse
from froofle.protobuf.service import RpcController

from codereview.models import Account, Settings
from codereview.internal.util import InternalAPI
from codereview.view_util import xsrf_for, is_xsrf_ok

from codereview.backup_service import BackupServiceImp
from codereview.review_service import ReviewServiceImp
from codereview.internal.admin_service import AdminServiceImp
from codereview.internal.build_service import BuildServiceImp
from codereview.internal.bundle_store_service import BundleStoreServiceImp
from codereview.internal.change_service import ChangeServiceImp
from codereview.internal.merge_service import MergeServiceImp

MAX_TIME_WINDOW = 5 * 60 # seconds
XSRF_PATH = '/proto/'
services = {}

def register_service(s_impl):
  services[s_impl.GetDescriptor().name] = s_impl

register_service(BackupServiceImp())
register_service(ReviewServiceImp())
register_service(AdminServiceImp())
register_service(BuildServiceImp())
register_service(BundleStoreServiceImp())
register_service(ChangeServiceImp())
register_service(MergeServiceImp())

class _LocalController(RpcController):
  def __init__(self, s_impl):
    self._failed = None
    self._service = s_impl

  def Reset(self):
    pass

  def Failed(self):
    pass

  def ErrorText(self):
    pass

  def StartCancel(self):
    pass

  def SetFailed(self, reason):
    logging.error("Failed on %s: %s" % (
      self._service.__class__.__name__,
      reason))
    self._failed = reason

  def IsCancelled(self):
    pass

  def NotifyOnCancel(self, callback):
    pass

  def HasFailed(self):
    return self._failed is not None

def token(req):
  user = users.get_current_user()
  if not user:
    return HttpResponse(status=401, content="User must be logged in.")
  return HttpResponse(content = xsrf_for(XSRF_PATH),
                      content_type = 'application/octet-stream')

def serve(req, service_name, action_name):
  try:
    type_str = req.META['CONTENT_TYPE']
  except KeyError:
    return HttpResponse(status=415, content="Invalid request body type")

  type = type_str.split('; ')
  type_dict = {}
  type_dict['name'] = None
  type_dict['compress'] = None
  for t in type[1:]:
    name, val = t.split('=', 1)
    type_dict[name] = val
  if type[0] != "application/x-google-protobuf":
    return HttpResponse(status=415, content="Invalid request body type")

  try: s_impl = services[service_name]
  except KeyError:
    return HttpResponse(status=404, content="Service not recognized.")

  method = s_impl.GetDescriptor().FindMethodByName(action_name)
  if not method:
    return HttpResponse(status=404, content="Method not recognized.")

  request = s_impl.GetRequestClass(method)()
  request_name = request.DESCRIPTOR.full_name
  if type_dict['name'] != request_name:
    return HttpResponse(status=415,
                        content="Expected a %s" % request_name)

  raw_body = req.raw_post_data
  msg_bin = raw_body

  if 'HTTP_CONTENT_MD5' in req.META:
    expmd5 = req.META['HTTP_CONTENT_MD5']

    actmd5 = hashlib.md5()
    actmd5.update(raw_body)
    actmd5 = base64.b64encode(actmd5.digest())

    if actmd5 != expmd5:
      return HttpResponse(status=412,
                          content="Content-MD5 incorrect")

  compression = type_dict['compress']
  if compression == 'deflate':
    msg_bin = zlib.decompress(msg_bin)
  elif compression:
    return HttpResponse(status=415,
                        content="Unsupported compression %s" % compression)

  if isinstance(s_impl, InternalAPI):
    key = Settings.get_settings().internal_api_key
    key = base64.b64decode(key)

    try:
      date = int(req.META['HTTP_X_DATE_UTC'])
    except KeyError:
      return HttpResponse(status=403,
                          content="X-Date-UTC header is required.")

    try:
      exp_sig = req.META['HTTP_AUTHORIZATION']
    except KeyError:
      return HttpResponse(status=403,
                          content="Authorization header is required.")

    if not exp_sig.startswith("proto :"):
      return HttpResponse(status=403,
                          content="Malformed authorization header.")
    exp_sig = exp_sig[len("proto :"):]

    now = time.time()
    if abs(date - now) > MAX_TIME_WINDOW:
      return HttpResponse(status=403,
                          content="Request is too early or too late.")

    m = hmac.new(key, digestmod=hashlib.sha1)
    m.update('POST %s\n' % req.path)
    m.update('X-Date-UTC: %s\n' % date)
    m.update('Content-Type: %s\n' % type_str)
    m.update('\n')
    m.update(raw_body)
    if base64.b64encode(m.digest()) != exp_sig:
      return HttpResponse(status=403,
                          content="Invalid request signature.")
  else:
    user = users.get_current_user()
    if not user:
      return HttpResponse(status=401, content="User must be logged in.")

    try:
      xsrf = req.META['HTTP_X_XSRF_TOKEN']
    except KeyError:
      return HttpResponse(status=403,
                          content="X-XSRF-Token header required.")
    if not is_xsrf_ok(req, path=XSRF_PATH, xsrf=xsrf):
      return HttpResponse(status=403,
                          content="X-XSRF-Token invalid.")

  request.ParseFromString(msg_bin)
  controller = _LocalController(s_impl)

  class result_caddy:
    _r = HttpResponse(status=500)
    def __call__(self, r):
      if r is not None:
        r_bin = r.SerializeToString()
        r_name = r.DESCRIPTOR.full_name
        r_type = "application/x-google-protobuf; name=%s" % r_name
        self._r = HttpResponse(content_type = r_type, content = r_bin)
  done = result_caddy()

  s_impl.http_request = req

  s_impl.CallMethod(method, controller, request, done)
  if controller.HasFailed():
    return HttpResponse(status=500)
  return done._r
