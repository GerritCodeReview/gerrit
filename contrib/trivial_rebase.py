#!/usr/bin/env python

# Copyright (c) 2010, Code Aurora Forum. All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are
# met:
#    # Redistributions of source code must retain the above copyright
#       notice, this list of conditions and the following disclaimer.
#    # Redistributions in binary form must reproduce the above
#       copyright notice, this list of conditions and the following
#       disclaimer in the documentation and/or other materials provided
#       with the distribution.
#    # Neither the name of Code Aurora Forum, Inc. nor the names of its
#       contributors may be used to endorse or promote products derived
#       from this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
# WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
# ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
# BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
# CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
# SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
# BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
# WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
# OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
# IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

# This script is designed to detect when a patchset uploaded to Gerrit is
# 'identical' (determined via git-patch-id) and reapply reviews onto the new
# patchset from the previous patchset.

# Get usage and help info by running: ./trivial_rebase.py --help
# Documentation is available here: https://www.codeaurora.org/xwiki/bin/QAEP/Gerrit

import argparse
import json
import re
import subprocess

class TrivialRebase:
  def __init__(self):
    self.server = 'localhost'
    usage = "%(prog)s <required options> [--server-port=PORT]"
    parser = argparse.ArgumentParser(usage=usage)
    parser.add_argument("--change-url", dest="changeUrl", help="Change URL")
    parser.add_argument("--project", help="Project path in Gerrit")
    parser.add_argument("--commit", help="Git commit-ish for this patchset")
    parser.add_argument("--patchset", type=int, help="The patchset number")
    parser.add_argument("--private-key-path", dest="private_key_path",
                        help="Full path to Gerrit SSH daemon's private host key")
    parser.add_argument("--server-port", dest="port", default='29418',
                        help="Port to connect to Gerrit's SSH daemon "
                             "[default: %(default)s]")
    args = parser.parse_known_args()[0]
    if (args.changeUrl is None or
        args.project is None or
        args.commit is None or
        args.patchset is None):
      parser.error("Incomplete arguments")
    try:
      self.changeId = re.search(r'\d+$', args.changeUrl).group()
    except AttributeError:
      parser.error("Invalid changeId")
    self.project = args.project
    self.commit = args.commit
    self.patchset = args.patchset
    self.private_key_path = args.private_key_path
    self.port = args.port

  class CheckCallError(OSError):
    """CheckCall() returned non-0."""
    def __init__(self, command, cwd, retcode, stdout, stderr=None):
      OSError.__init__(self, command, cwd, retcode, stdout, stderr)
      self.command = command
      self.cwd = cwd
      self.retcode = retcode
      self.stdout = stdout
      self.stderr = stderr

  def CheckCall(self, command, cwd=None):
    """Like subprocess.check_call() but returns stdout.

    Works on python 2.4
    """
    try:
      process = subprocess.Popen(command, cwd=cwd, stdout=subprocess.PIPE)
      std_out, std_err = process.communicate()
    except OSError, e:
      raise self.CheckCallError(command, cwd, e.errno, None)
    if process.returncode:
      raise self.CheckCallError(command, cwd, process.returncode, std_out, std_err)
    return std_out, std_err

  def GsqlQuery(self, sql_query):
    """Runs a gerrit gsql query and returns the result"""
    gsql_cmd = ['ssh', '-p', self.port, self.server, 'gerrit', 'gsql',
                '--format', 'JSON', '-c', sql_query]
    try:
      (gsql_out, _gsql_stderr) = self.CheckCall(gsql_cmd)
    except self.CheckCallError, e:
      print "return code is %s" % e.retcode
      print "stdout and stderr is\n%s%s" % (e.stdout, e.stderr)
      raise

    new_out = gsql_out.replace('}}\n', '}}\nsplit here\n')
    return new_out.split('split here\n')

  def FindPrevRev(self):
    """Finds the revision of the previous patch set on the change"""
    sql_query = ("\"SELECT revision FROM patch_sets WHERE "
                 "change_id = %s AND patch_set_id = %s\"" %
                 (self.changeId, (self.patchset - 1)))
    revisions = self.GsqlQuery(sql_query)

    json_dict = json.loads(revisions[0], strict=False)
    return json_dict["columns"]["revision"]

  def GetApprovals(self):
    """Get all the approvals on a specific patch set

    Returns a list of approval dicts"""
    sql_query = ("\"SELECT value,account_id,category_id FROM patch_set_approvals "
                 "WHERE change_id = %s AND patch_set_id = %s AND value != 0\""
                 % (self.changeId, (self.patchset - 1)))
    gsql_out = self.GsqlQuery(sql_query)
    approvals = []
    for json_str in gsql_out:
      data = json.loads(json_str, strict=False)
      if data["type"] == "row":
        approvals.append(data["columns"])
    return approvals

  def GetEmailFromAcctId(self, account_id):
    """Returns the preferred email address associated with the account_id"""
    sql_query = ("\"SELECT preferred_email FROM accounts WHERE account_id = %s\""
                 % account_id)
    email_addr = self.GsqlQuery(sql_query)

    json_dict = json.loads(email_addr[0], strict=False)
    return json_dict["columns"]["preferred_email"]

  def GetPatchId(self, revision):
    git_show_cmd = ['git', 'show', revision]
    patch_id_cmd = ['git', 'patch-id']
    patch_id_process = subprocess.Popen(patch_id_cmd, stdout=subprocess.PIPE,
                                        stdin=subprocess.PIPE)
    git_show_process = subprocess.Popen(git_show_cmd, stdout=subprocess.PIPE)
    return patch_id_process.communicate(git_show_process.communicate()[0])[0]

  def SuExec(self, as_user, cmd):
    suexec_cmd = ['ssh', '-l', "Gerrit Code Review", '-p', self.port, self.server]
    if self.private_key_path:
      suexec_cmd += ['-i', self.private_key_path]
    suexec_cmd += ['suexec', '--as', as_user, '--', cmd]
    self.CheckCall(suexec_cmd)

  def DiffCommitMessages(self, prev_commit):
    log_cmd1 = ['git', 'log', '--pretty=format:"%an %ae%n%s%n%b"',
                prev_commit + '^!']
    commit1_log = self.CheckCall(log_cmd1)
    log_cmd2 = ['git', 'log', '--pretty=format:"%an %ae%n%s%n%b"',
                self.commit + '^!']
    commit2_log = self.CheckCall(log_cmd2)
    if commit1_log != commit2_log:
      return True
    return False

  def Run(self):
    if self.patchset == 1:
      # Nothing to detect on first patchset
      return
    prev_revision = self.FindPrevRev()
    if not prev_revision:
      # Couldn't find a previous revision
      return
    prev_patch_id = self.GetPatchId(prev_revision)
    cur_patch_id = self.GetPatchId(self.commit)
    if not (prev_patch_id and cur_patch_id):
      # Merge commit
      if not prev_patch_id:
        print "GetPatchId failed for commit %s" % (prev_revision)
      if not cur_patch_id:
        print "GetPatchId failed for commit %s" % (self.commit)
      return
    if cur_patch_id.split()[0] != prev_patch_id.split()[0]:
      # patch-ids don't match
      return
    # Patch ids match. This is a trivial rebase.
    # In addition to patch-id we should check if the commit message changed. Most
    # approvers would want to re-review changes when the commit message changes.
    changed = self.DiffCommitMessages(prev_revision)
    if changed:
      # Insert a comment into the change letting the approvers know only the
      # commit message changed
      comment_msg = ("\'--message=New patchset patch-id matches previous patchset"
                     ", but commit message has changed.'")
      comment_cmd = ['ssh', '-p', self.port, self.server, 'gerrit', 'approve',
                     '--project', self.project, comment_msg, self.commit]
      self.CheckCall(comment_cmd)
      return

    # Need to get all approvals on prior patch set, then suexec them onto
    # this patchset.
    approvals = self.GetApprovals()
    gerrit_approve_msg = ("\'Automatically re-added by Gerrit trivial rebase "
                          "detection script.\'")
    for approval in approvals:
      # Note: Sites with different 'copy_min_score' values in the
      # approval_categories DB table might want different behavior here.
      # Additional categories should also be added if desired.
      if approval["category_id"] == "CRVW":
        approve_category = '--code-review'
      elif approval["category_id"] == "VRIF":
        # Don't re-add verifies
        #approve_category = '--verified'
        continue
      elif approval["category_id"] == "SUBM":
        # We don't care about previous submit attempts
        continue
      else:
        print "Unsupported category: %s" % approval
        return

      score = approval["value"]
      gerrit_approve_cmd = ['gerrit', 'approve', '--project', self.project,
                            '--message', gerrit_approve_msg, approve_category,
                            score, self.commit]
      email_addr = self.GetEmailFromAcctId(approval["account_id"])
      self.SuExec(email_addr, ' '.join(gerrit_approve_cmd))

if __name__ == "__main__":
  TrivialRebase().Run()
