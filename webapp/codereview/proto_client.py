# Copyright 2007, 2008 Google Inc.
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
import cookielib
import getpass
import logging
import md5
import os
import random
import socket
import sys
import time
import urllib
import urllib2
import urlparse

from froofle.protobuf.service import RpcChannel
from froofle.protobuf.service import RpcController
from need_retry_pb2 import RetryRequestLaterResponse;

_cookie_jars = {}

def _open_jar(path):
  auth = False

  if path is None:
    c = cookielib.CookieJar()
  else:
    c = _cookie_jars.get(path)
    if c is None:
      c = cookielib.MozillaCookieJar(path)

      if os.path.exists(path):
        try:
          c.load()
          auth = True
        except (cookielib.LoadError, IOError):
          pass

        if auth:
          print >>sys.stderr, \
                'Loaded authentication cookies from %s' \
                % path
      else:
        os.close(os.open(path, os.O_CREAT, 0600))
      os.chmod(path, 0600)
      _cookie_jars[path] = c
    else:
      auth = True
  return c, auth


class ClientLoginError(urllib2.HTTPError):
  """Raised to indicate an error authenticating with ClientLogin."""

  def __init__(self, url, code, msg, headers, args):
    urllib2.HTTPError.__init__(self, url, code, msg, headers, None)
    self.args = args
    self.reason = args["Error"]


class Proxy(object):
  class _ResultHolder(object):
    def __call__(self, result):
      self._result = result

  class _RemoteController(RpcController):
    def Reset(self):
      pass
  
    def Failed(self):
      pass
  
    def ErrorText(self):
      pass
  
    def StartCancel(self):
      pass
  
    def SetFailed(self, reason):
      raise RuntimeError, reason
  
    def IsCancelled(self):
      pass
  
    def NotifyOnCancel(self, callback):
      pass
  
  def __init__(self, stub):
    self._stub = stub

  def __getattr__(self, key):
    method = getattr(self._stub, key)

    def call(request):
      done = self._ResultHolder()
      method(self._RemoteController(), request, done)
      return done._result

    return call


class HttpRpc(RpcChannel):
  """Simple protobuf over HTTP POST implementation."""

  def __init__(self, host, auth_function,
               host_override=None,
               extra_headers={},
               cookie_file=None):
    """Creates a new HttpRpc.

    Args:
      host: The host to send requests to.
      auth_function: A function that takes no arguments and returns an
        (email, password) tuple when called. Will be called if authentication
        is required.
      host_override: The host header to send to the server (defaults to host).
      extra_headers: A dict of extra headers to append to every request.
      cookie_file: If not None, name of the file in ~/ to save the
        cookie jar into.  Applications are encouraged to set this to
        '.$appname_cookies' or some otherwise unique name.
    """
    self.host = host.lower()
    self.host_override = host_override
    self.auth_function = auth_function
    self.authenticated = False
    self.extra_headers = extra_headers
    self.xsrf_token = None
    if cookie_file is None:
      self.cookie_file = None
    else:
      self.cookie_file = os.path.expanduser("~/%s" % cookie_file)
    self.opener = self._GetOpener()
    if self.host_override:
      logging.info("Server: %s; Host: %s", self.host, self.host_override)
    else:
      logging.info("Server: %s", self.host)

  def CallMethod(self, method, controller, request, response_type, done):
    pat = "application/x-google-protobuf; name=%s"

    url = "/proto/%s/%s" % (method.containing_service.name, method.name)
    reqbin = request.SerializeToString()
    reqtyp = pat % request.DESCRIPTOR.full_name
    reqmd5 = base64.b64encode(md5.new(reqbin).digest())

    start = time.time()
    while True:
      t, b = self._Send(url, reqbin, reqtyp, reqmd5)
      if t == (pat % RetryRequestLaterResponse.DESCRIPTOR.full_name):
        if time.time() >= (start + 1800):
          controller.SetFailed("timeout")
          return
        s = random.uniform(0.250, 2.000)
        print "Busy, retrying in %.3f seconds ..." % s
        time.sleep(s)
        continue

      if t == (pat % response_type.DESCRIPTOR.full_name):
        response = response_type()
        response.ParseFromString(b)
        done(response)
      else:
        controller.SetFailed("Unexpected %s response" % t)
      break

  def _CreateRequest(self, url, data=None):
    """Creates a new urllib request."""
    logging.debug("Creating request for: '%s' with payload:\n%s", url, data)
    req = urllib2.Request(url, data=data)
    if self.host_override:
      req.add_header("Host", self.host_override)
    for key, value in self.extra_headers.iteritems():
      req.add_header(key, value)
    return req

  def _GetAuthToken(self, email, password):
    """Uses ClientLogin to authenticate the user, returning an auth token.

    Args:
      email:    The user's email address
      password: The user's password

    Raises:
      ClientLoginError: If there was an error authenticating with ClientLogin.
      HTTPError: If there was some other form of HTTP error.

    Returns:
      The authentication token returned by ClientLogin.
    """
    account_type = 'GOOGLE'
    if self.host.endswith('.google.com'):
      account_type = 'HOSTED'

    req = self._CreateRequest(
        url="https://www.google.com/accounts/ClientLogin",
        data=urllib.urlencode({
            "Email": email,
            "Passwd": password,
            "service": "ah",
            "source": "gerrit-codereview-client",
            "accountType": account_type,
        })
    )
    try:
      response = self.opener.open(req)
      response_body = response.read()
      response_dict = dict(x.split("=")
                           for x in response_body.split("\n") if x)
      return response_dict["Auth"]
    except urllib2.HTTPError, e:
      if e.code == 403:
        body = e.read()
        response_dict = dict(x.split("=", 1) for x in body.split("\n") if x)
        raise ClientLoginError(req.get_full_url(), e.code, e.msg,
                               e.headers, response_dict)
      else:
        raise

  def _GetAuthCookie(self, auth_token):
    """Fetches authentication cookies for an authentication token.

    Args:
      auth_token: The authentication token returned by ClientLogin.

    Raises:
      HTTPError: If there was an error fetching the authentication cookies.
    """
    # This is a dummy value to allow us to identify when we're successful.
    continue_location = "http://localhost/"
    args = {"continue": continue_location, "auth": auth_token}
    req = self._CreateRequest("http://%s/_ah/login?%s" %
                              (self.host, urllib.urlencode(args)))
    try:
      response = self.opener.open(req)
    except urllib2.HTTPError, e:
      response = e
    if (response.code != 302 or
        response.info()["location"] != continue_location):
      raise urllib2.HTTPError(req.get_full_url(), response.code, response.msg,
                              response.headers, response.fp)

  def _GetXsrfToken(self):
    """Fetches /proto/_token for use in X-XSRF-Token HTTP header.

    Raises:
      HTTPError: If there was an error fetching a new token.
    """
    tries = 0
    while True:
      url = "http://%s/proto/_token" % self.host
      req = self._CreateRequest(url)
      try:
        response = self.opener.open(req)
        self.xsrf_token = response.read()
        return
      except urllib2.HTTPError, e:
        if tries > 3:
          raise
        elif e.code == 401:
          self._Authenticate()
        else:
          raise

  def _Authenticate(self):
    """Authenticates the user.

    The authentication process works as follows:
     1) We get a username and password from the user
     2) We use ClientLogin to obtain an AUTH token for the user
        (see http://code.google.com/apis/accounts/AuthForInstalledApps.html).
     3) We pass the auth token to /_ah/login on the server to obtain an
        authentication cookie. If login was successful, it tries to redirect
        us to the URL we provided.

    If we attempt to access the upload API without first obtaining an
    authentication cookie, it returns a 401 response and directs us to
    authenticate ourselves with ClientLogin.
    """
    attempts = 0
    while True:
      attempts += 1
      try:
        cred = self.auth_function()
        auth_token = self._GetAuthToken(cred[0], cred[1])
      except ClientLoginError:
        if attempts < 3:
          continue
        raise
      self._GetAuthCookie(auth_token)
      self.authenticated = True
      if self.cookie_file is not None:
        print >>sys.stderr, \
              'Saving authentication cookies to %s' \
              % self.cookie_file
        self.cookie_jar.save()
      return

  def _Send(self, request_path, payload, content_type, content_md5):
    """Sends an RPC and returns the response.

    Args:
      request_path: The path to send the request to, eg /api/appversion/create.
      payload: The body of the request, or None to send an empty request.
      content_type: The Content-Type header to use.
      content_md5: The Content-MD5 header to use.

    Returns:
      The content type, as a string.
      The response body, as a string.
    """
    if not self.authenticated:
      self._Authenticate()
    if not self.xsrf_token:
      self._GetXsrfToken()

    old_timeout = socket.getdefaulttimeout()
    socket.setdefaulttimeout(None)
    try:
      tries = 0
      while True:
        tries += 1
        url = "http://%s%s" % (self.host, request_path)
        req = self._CreateRequest(url=url, data=payload)
        req.add_header("Content-Type", content_type)
        req.add_header("Content-MD5", content_md5)
        req.add_header("X-XSRF-Token", self.xsrf_token)
        try:
          f = self.opener.open(req)
          hdr = f.info()
          type = hdr.getheader('Content-Type',
                               'application/octet-stream')
          response = f.read()
          f.close()
          return type, response
        except urllib2.HTTPError, e:
          if tries > 3:
            raise
          elif e.code == 401:
            self._Authenticate()
          elif e.code == 403:
            if not hasattr(e, 'read'):
              e.read = lambda self: ''
            raise RuntimeError, '403\nxsrf: %s\n%s' \
                  % (self.xsrf_token, e.read())
          else:
            raise
    finally:
      socket.setdefaulttimeout(old_timeout)

  def _GetOpener(self):
    """Returns an OpenerDirector that supports cookies and ignores redirects.

    Returns:
      A urllib2.OpenerDirector object.
    """
    opener = urllib2.OpenerDirector()
    opener.add_handler(urllib2.ProxyHandler())
    opener.add_handler(urllib2.UnknownHandler())
    opener.add_handler(urllib2.HTTPHandler())
    opener.add_handler(urllib2.HTTPDefaultErrorHandler())
    opener.add_handler(urllib2.HTTPSHandler())
    opener.add_handler(urllib2.HTTPErrorProcessor())

    self.cookie_jar, \
    self.authenticated = _open_jar(self.cookie_file)
    opener.add_handler(urllib2.HTTPCookieProcessor(self.cookie_jar))
    return opener
