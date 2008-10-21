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

"""Custom middleware.  Some of this may be generally useful."""

import logging
import time

from google.appengine.api import users
from django.utils.http import http_date

import models
import view_util
import library

class NoCacheMiddleware(object):
  """Set the cache-control and expires headers."""

  def process_response(self, request, response):
    response['Cache-Control'] = 'no-cache'
    response['Expires'] = http_date(time.time() - 1)
    return response


class ClearXsrfKeyMiddleware(object):
  """Sets the xsrf_key to None so it can later be read."""

  def process_request(self, request):
    view_util._xsrf_key = None
    view_util._xsrf_now = None
    view_util._xsrf_cache = {}
    library._user_cache.clear_local()


class AddUserToRequestMiddleware(object):
  """Add a user object and a user_is_admin flag to each request."""

  def process_request(self, request):
    request.user = users.get_current_user()
    if request.user:
      request.is_gae_admin = users.is_current_user_admin()
      request.user_is_admin = models.AccountGroup.is_user_admin(request.user)
      request.projects_owned_by_user = models.Project.projects_owned_by_user(
          request.user)
      request.show_admin_tab = (request.user_is_admin
          or len(request.projects_owned_by_user) > 0)
    else:
      request.is_gae_admin = False
      request.user_is_admin = False
      request.projects_owend_by_user = set()
      request.show_admin_tab = False

    # Update the cached value of the current user's Account
    account = None
    if request.user is not None:
      account = models.Account.get_account_for_user(request.user)
      request.account = account
    else:
      request.account = None
    models.Account.current_user_account = account
