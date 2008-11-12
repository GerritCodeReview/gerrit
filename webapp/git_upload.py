#!/usr/bin/env python2.5
#
# Copyright 2007 Google Inc.
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

import getpass
import logging
import optparse
import os
import subprocess
import sys
from tempfile import mkstemp

from codereview.proto_client import HttpRpc, Proxy
from codereview.review_pb2 import ReviewService_Stub
from codereview.upload_bundle_pb2 import *

try:
  import readline
except ImportError:
  pass

MAX_SEGMENT_SIZE = 1020 * 1024

# The logging verbosity:
#  0: Errors only.
#  1: Status messages.
#  2: Info logs.
#  3: Debug logs.
verbosity = 1

def StatusUpdate(msg):
  """Print a status message to stdout.

  If 'verbosity' is greater than 0, print the message.

  Args:
    msg: The string to print.
  """
  if verbosity > 0:
    print msg


def ErrorExit(msg):
  """Print an error message to stderr and exit."""
  print >>sys.stderr, msg
  sys.exit(1)


def RunShell(command, args=(), silent_ok=False):
  command = "%s %s" % (command, " ".join(args))
  logging.info("Running %s", command)
  stream = os.popen(command, "r")
  data = stream.read()
  if stream.close():
    ErrorExit("Got error status from %s" % command)
  if not silent_ok and not data:
    ErrorExit("No output from %s" % command)
  return data


def RunGit(*args):
  argv = ["git"]
  argv += args
  retcode = subprocess.call(argv)
  if retcode != 0:
    raise OSError, retcode

def GitVal(*args):
  data = RunShell("git", args)
  if data.rfind("\n") == len(data) - 1:
    return data[0 : len(data) - 1]
  return data


parser = optparse.OptionParser(usage="%prog [options] [-- diff_options]")

# Logging
group = parser.add_option_group("Logging options")
group.add_option("-q", "--quiet", action="store_const", const=0,
                 dest="verbose", help="Print errors only.")
group.add_option("-v", "--verbose", action="store_const", const=2,
                 dest="verbose", default=1,
                 help="Print info level logs (default).")
group.add_option("--noisy", action="store_const", const=3,
                 dest="verbose", help="Print all logs.")

# Review server
group = parser.add_option_group("Review server options")
group.add_option("-s", "--server", action="store", dest="server",
                 default="codereview.appspot.com",
                 metavar="SERVER",
                 help=("The server to upload to. The format is host[:port]. "
                       "Defaults to 'codereview.appspot.com'."))
group.add_option("-e", "--email", action="store", dest="email",
                 metavar="EMAIL", default=None,
                 help="The username to use. Will prompt if omitted.")
group.add_option("-H", "--host", action="store", dest="host",
                 metavar="HOST", default=None,
                 help="Overrides the Host header sent with all RPCs.")
group.add_option("--no_cookies", action="store_false",
                 dest="save_cookies", default=True,
                 help="Do not save authentication cookies to local disk.")

# Git
group = parser.add_option_group("Git options")
group.add_option("-p", "--project", action="store", dest="dest_project",
                 metavar="PROJECT",
                 help=("Name of the Git repository to submit into."))
group.add_option("-b", "--branch", action="store", dest="dest_branch",
                 metavar="BRANCH",
                 help=("Name of the branch the changes are proposed for."))
group.add_option("-B", "--base", action="store", dest="base_commit",
                 default="refs/remotes/origin/master",
                 metavar="COMMIT",
                 help=("Base commit for the bundle."))
group.add_option('-r', '--replace', action='append', dest='replace',
                 metavar='CHANGE:COMMIT',
                 help='Replace a patch set on an existing change')

def GetRpcServer(options):
  """Returns an RpcServer.

  Returns:
    A new RpcServer, on which RPC calls can be made.
  """

  def GetUserCredentials():
    """Prompts the user for a username and password."""
    email = options.email
    if email is None:
      email = raw_input("Email: ").strip()
    password = getpass.getpass("Password for %s: " % email)
    return (email, password)

  # If this is the dev_appserver, use fake authentication.
  host = (options.host or options.server).lower()
  if host == "localhost" or host.startswith("localhost:"):
    email = options.email
    if email is None:
      email = "test@example.com"
      logging.info("Using debug user %s.  Override with --email" % email)

    server = HttpRpc(
        options.server,
        lambda: (email, "password"),
        host_override=options.host,
        extra_headers={"Cookie":
                       'dev_appserver_login="%s:False"' % email})
    # Don't try to talk to ClientLogin.
    server.authenticated = True
    return server

  if options.save_cookies:
    cookie_file = ".gerrit_cookies"
  else:
    cookie_file = None

  return HttpRpc(options.server, GetUserCredentials,
                 host_override=options.host,
                 cookie_file=cookie_file)


def RealMain(argv, data=None):
  logging.basicConfig(format=("%(asctime).19s %(levelname)s %(filename)s:"
                              "%(lineno)s %(message)s "))
  os.environ['LC_ALL'] = 'C'
  options, args = parser.parse_args(argv[1:])

  global verbosity
  verbosity = options.verbose
  if verbosity >= 3:
    logging.getLogger().setLevel(logging.DEBUG)
  elif verbosity >= 2:
    logging.getLogger().setLevel(logging.INFO)

  srv = GetRpcServer(options)
  review = Proxy(ReviewService_Stub(srv))

  git_dir = GitVal("rev-parse","--git-dir")

  revlist = GitVal("rev-list",
                   "^" + options.base_commit,
                   "HEAD").split("\n")

  replace_changes = dict()
  if options.replace:
    for line in options.replace:
      change_id, commit_id = line.split(':')
      replace_changes[change_id] = commit_id

  tmp_fd, tmp_bundle = mkstemp(".bundle", ".gpq", git_dir)
  os.close(tmp_fd)

  try:
    RunGit("bundle", "create",
           tmp_bundle,
           "^" + options.base_commit,
           "HEAD")
    fd = open(tmp_bundle, "rb")

    bundle_id = None
    segment_id = 0
    next_data = fd.read(MAX_SEGMENT_SIZE)

    while len(next_data) > 0:
      this_data = next_data
      next_data = fd.read(MAX_SEGMENT_SIZE)
      segment_id += 1
    
      if bundle_id is None:
        req = UploadBundleRequest()
        req.dest_project = options.dest_project
        req.dest_branch = options.dest_branch
        for c in revlist:
          req.contained_object.append(c)
        for change_id,commit_id in replace_changes.iteritems():
          r = req.replace.add()
          r.change_id = change_id
          r.object_id = commit_id
      else:
        req = UploadBundleContinue()
        req.bundle_id = bundle_id
        req.segment_id = segment_id

      req.bundle_data = this_data
      if len(next_data) > 0:
        req.partial_upload = True
      else:
        req.partial_upload = False

      if bundle_id is None:
        rsp = review.UploadBundle(req)
      else:
        rsp = review.ContinueBundle(req)

      if rsp.status_code == UploadBundleResponse.CONTINUE:
        bundle_id = rsp.bundle_id
      else:
        print rsp
        break
  finally:
    os.unlink(tmp_bundle)

def main():
  try:
    RealMain(sys.argv)
  except KeyboardInterrupt:
    print
    StatusUpdate("Interrupted.")
    sys.exit(1)


if __name__ == "__main__":
  main()
