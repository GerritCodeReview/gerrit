#!/usr/bin/env python2.4
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

import binascii
import base64
import sha
import zlib
import getpass
import logging
import optparse
import os
import re
import sys
from xml.dom.minidom import parseString
from pyPgSQL import PgSQL
from pyPgSQL.libpq import PgQuoteBytea, OperationalError

from codereview.proto_client import HttpRpc, Proxy
from codereview.backup_pb2 import *

KINDS = [
  "ApprovalRight",
  "Project",
  "Branch",
  "RevisionId",
  "Change",
  "PatchSet",
  "Message",
  "Patch",
  "Comment",
  "ReviewStatus",
  "Account",
  "AccountGroup",
]
[
  "DeltaContent",
  "Settings",
  "BuildAttempt",
  "PatchSetFilenames",
  "Bucket",
]

try:
  import readline
except ImportError:
  pass

parser = optparse.OptionParser(usage="%prog [options] [-- diff_options]")

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

group = parser.add_option_group("Backup database options")
group.add_option("-d", action="store", dest="dbname",
                 metavar="DBNAME",
                 help="PostgreSQL database name")

def GetRpcServer(options):
  def GetUserCredentials():
    email = options.email
    if email is None:
      email = raw_input("Email: ").strip()
    password = getpass.getpass("Password for %s: " % email)
    return (email, password)

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
    server.authenticated = True
    return server

  if options.save_cookies:
    cookie_file = ".gerrit_cookies"
  else:
    cookie_file = None

  return HttpRpc(options.server, GetUserCredentials,
                 host_override=options.host,
                 cookie_file=cookie_file)

def getText(nodelist):
  rc = ""
  for node in nodelist:
    if node.nodeType == node.TEXT_NODE:
      rc = rc + node.data
  return rc

key_re = re.compile(r'^tag:.*\[(.*)\]$')

def parse_dom(dom):
  class AnyObject(object):
    def __getattr__(self, name):
      return []

  o = AnyObject()
  for p in dom.getElementsByTagName('property'):
    n = p.getAttribute('name')
    v = getText(p.childNodes)
    t = p.getAttribute('type')
    if t == 'null':
      continue

    if t == 'key':
      v = key_re.match(v).group(1)
    elif t == 'int':
      v = int(v)
    elif t == 'bool':
      if v == 'True':
        v = True
      elif v == 'False':
        v = False
    elif t == 'gd:email':
      v = p.getElementsByTagName('gd:email')[0].getAttribute('address')
      if v and '@' not in v:
        v += '@gmail.com'
    elif t == 'user':
      if v and '@' not in v:
        v += '@gmail.com'

    a = getattr(o, n, [])
    if v != '':
      a.append(v)
    setattr(o, n, a)
  return o

def one(v):
  if len(v) == 1:
    return v[0]
  return None

def yn(v):
  if one(v):
    return 'Y'
  return 'N'

def yn_null(v):
  if len(v) == 1 and v[0] is not None:
    return yn(v)
  return None


class LocalStore(object):
  def __init__(self, db):
    self.db = db

  def delete(self, table_name, entity):
    c = self.db.cursor()
    c.execute('DELETE FROM ' + table_name + ' WHERE gae_key=%s',
              (entity.key))

  def insert(self, table_name, dict, base64_keys=[]):
    p = []
    for u in dict.keys():
      if u in base64_keys:
        p.append("decode(%s,'base64')")
      else:
        p.append('%s')

    s = 'INSERT INTO ' + table_name + '(' + ','.join(dict.keys()) + ')'
    s += 'VALUES(' + ','.join(p) + ')'
    c = self.db.cursor()
    try:
      c.execute(s, dict.values())
    except OperationalError:
      print 'FAIL %s %s' % (table_name, dict)
      raise

  def save_ApprovalRight(self, entity, obj):
    ar_id = entity.key_id
    self.delete('approval_rights', entity)
    self.insert('approval_rights', {
      'ar_id': ar_id,
      'gae_key': entity.key,
      'last_backed_up': one(obj.last_backed_up),

      'required': yn(obj.required),
    })

    for p in obj.files:
      self.insert('approval_right_files', {'ar_id':ar_id, 'path':p})

    for u in obj.approvers_users:
      self.insert('approval_right_users', {'ar_id':ar_id, 'email':u, 'type': 'approver'})
    for u in obj.verifiers_users:
      self.insert('approval_right_users', {'ar_id':ar_id, 'email':u, 'type': 'verifier'})
    for u in obj.submitters_users:
      self.insert('approval_right_users', {'ar_id':ar_id, 'email':u, 'type': 'submitter'})

    for u in obj.approvers_groups:
      self.insert('approval_right_groups', {'ar_id':ar_id, 'group_key':u, 'type': 'approver'})
    for u in obj.verifiers_groups:
      self.insert('approval_right_groups', {'ar_id':ar_id, 'group_key':u, 'type': 'verifier'})
    for u in obj.submitters_groups:
      self.insert('approval_right_groups', {'ar_id':ar_id, 'group_key':u, 'type': 'submitter'})

  def save_Project(self, entity, obj):
    project_id = entity.key_id
    self.delete('projects', entity)
    self.insert('projects', {
      'project_id': project_id,
      'gae_key': entity.key,
      'last_backed_up': one(obj.last_backed_up),

      'name': one(obj.name),
      'comment': one(obj.comment),
    })

    for u in obj.owners_users:
      self.insert('project_owner_users', {'project_id':project_id, 'email':u})
    for u in obj.owners_groups:
      self.insert('project_owner_groups', {'project_id':project_id, 'group_key':u})
    for u in obj.code_reviews:
      self.insert('project_code_reviews', {'project_id':project_id, 'ar_key':u})

  def save_Branch(self, entity, obj):
    self.delete('branches', entity)
    self.insert('branches', {
      'gae_key': entity.key,
      'last_backed_up': one(obj.last_backed_up),
      'project_key': one(obj.project),
      'name': one(obj.name),
    })

  def save_RevisionId(self, entity, obj):
    self.delete('revisions', entity)
    self.insert('revisions', {
      'revision_id': one(obj.id),
      'gae_key': entity.key,
      'last_backed_up': one(obj.last_backed_up),
      'project_key': one(obj.project),

      'author_name': one(obj.author_name),
      'author_email': one(obj.author_email),
      'author_when': one(obj.author_when),
      'author_tz': one(obj.author_tz),

      'committer_name': one(obj.committer_name),
      'committer_email': one(obj.committer_email),
      'committer_when': one(obj.committer_when),
      'committer_tz': one(obj.committer_tz),

      'message': one(obj.message),
      'patchset_key': one(obj.patchset_key),
    })

    p = 1
    for a in obj.ancestors:
      self.insert('revision_ancestors', {
        'gae_key': entity.key,
        'child_id': one(obj.id),
        'parent_id': a,
        'position': p})
      p += 1

  def save_Change(self, entity, obj):
    change_id = entity.key_id
    self.delete('changes', entity)
    self.insert('changes', {
      'last_backed_up': one(obj.last_backed_up),
      'gae_key': entity.key,
      'change_id': change_id,
      'subject': one(obj.subject),
      'description': one(obj.description),
      'owner': one(obj.owner),
      'created': one(obj.created),
      'modified': one(obj.modified),
      'claimed': yn(obj.claimed),
      'closed': yn(obj.closed),
      'n_comments': one(obj.n_comments),
      'n_patchsets': one(obj.n_patchsets),
      'dest_project_key': one(obj.dest_project),
      'dest_branch_key': one(obj.dest_branch),
      'merge_submitted': one(obj.merge_submitted),
      'merged': yn(obj.merged),
      'emailed_clean_merge': yn(obj.emailed_clean_merge),
      'emailed_missing_dependency': yn(obj.emailed_missing_dependency),
      'emailed_path_conflict': yn(obj.emailed_path_conflict),
      'merge_patchset_key': one(obj.merge_patchset_key),
    })
    
    for u in obj.reviewers:
      self.insert('change_people', {'change_id':change_id,'email':u,'type':'reviewer'})
    for u in obj.cc:
      self.insert('change_people', {'change_id':change_id,'email':u,'type':'cc'})

  def save_PatchSet(self, entity, obj):
    self.delete('patch_sets', entity)
    self.insert('patch_sets', {
      'gae_key': entity.key,
      'last_backed_up': one(obj.last_backed_up),
      'patchset_id': one(obj.id),
      'change_key': one(obj.change),
      'message': one(obj.message),
      'owner': one(obj.owner),
      'created': one(obj.created),
      'modified': one(obj.modified),
      'revision_key': one(obj.revision),
      'complete': yn(obj.complete),
    })

  def save_Message(self, entity, obj):
    self.delete('messages', entity)
    self.insert('messages', {
      'gae_key': entity.key,
      'last_backed_up': one(obj.last_backed_up),
      'change_key': one(obj.change),
      'subject': one(obj.subject),
      'sender': one(obj.sender),
      'date_sent': one(obj.date),
      'body': one(obj.text),
    })

    for u in set(obj.recipients):
      self.insert('message_recipients', {'message_key':entity.key,'email':u})

  def save_DeltaContent(self, entity, obj):
    type, hash = entity.key_name.split(':')

    self.delete('delta_content', entity)
    self.insert('delta_content', {
      'gae_key': entity.key,
      'last_backed_up': one(obj.last_backed_up),
      'type': type,
      'hash': hash,
      'data_z': one(obj.text_z),
      'depth': one(obj.depth),
      'base_key': one(obj.base),
    }, set(['data_z']))

  def save_Patch(self, entity, obj):
    self.delete('patches', entity)
    self.insert('patches', {
      'gae_key': entity.key,
      'last_backed_up': one(obj.last_backed_up),
      'patchset_key': one(obj.patchset),
      'filename': one(obj.filename),
      'status': one(obj.status),
      'multi_way_diff': yn(obj.multi_way_diff),
      'n_comments': one(obj.n_comments),
      'old_data_key': one(obj.old_data),
      'new_data_key': one(obj.new_data),
      'diff_data_key': one(obj.diff_data),
    })

  def save_Comment(self, entity, obj):
    self.delete('comments', entity)
    self.insert('comments', {
      'gae_key': entity.key,
      'last_backed_up': one(obj.last_backed_up),
      'patch_key': one(obj.patch),
      'message_id': one(obj.message_id),
      'author': one(obj.author),
      'written': one(obj.date),
      'lineno': one(obj.lineno),
      'body': one(obj.text),
      'is_left': yn(obj.left),
      'draft': yn(obj.draft),
    })

  def save_ReviewStatus(self, entity, obj):
    self.delete('review_status', entity)
    self.insert('review_status', {
      'gae_key': entity.key,
      'last_backed_up': one(obj.last_backed_up),
      'change_key': one(obj.change),
      'email': one(obj.user),
      'lgtm': one(obj.lgtm),
      'verified': yn_null(obj.verified),
    })

  def save_Account(self, entity, obj):
    email = entity.key_name
    if email.startswith('<'):
      email = email[1:]
    if email.endswith('>'):
      email = email[:-1]
    if '@' not in email:
      email += '@gmail.com'

    self.delete('accounts', entity)
    self.insert('accounts', {
      'gae_key': entity.key,
      'last_backed_up': one(obj.last_backed_up),

      'user_email': one(obj.user),
      'email': email,
      'preferred_email': one(obj.preferred_email),
      'created': one(obj.created),
      'modified': one(obj.modified),

      'is_admin': yn(obj.is_admin),
      'welcomed': yn(obj.welcomed),
      'real_name_entered': yn(obj.real_name_entered),
      'real_name': one(obj.real_name),
      'mailing_address': one(obj.mailing_address),
      'mailing_address_country': one(obj.mailing_address_country),
      'phone_number': one(obj.phone_number),
      'fax_number': one(obj.fax_number),

      'cla_verified': yn(obj.cla_verified),
      'cla_verified_by': one(obj.cla_verified_by),
      'cla_verified_timestamp': one(obj.cla_verified_timestamp),
      'individual_cla_version': one(obj.individual_cla_version),
      'individual_cla_timestamp': one(obj.individual_cla_timestamp),
      'cla_comments': one(obj.cla_comments),

      'default_context': one(obj.default_context),
    })

    for i in set(obj.stars):
      self.insert('account_stars', {'email':email,'change_id':i})
    for i in set(obj.unclaimed_changes_projects):
      self.insert('account_unclaimed_changes_projects', {'email':email,'project_key':i})

  def save_AccountGroup(self, entity, obj):
    self.delete('account_groups', entity)
    self.insert('account_groups', {
      'gae_key': entity.key,
      'last_backed_up': one(obj.last_backed_up),
      'name': one(obj.name),
      'comment': one(obj.comment),
    })

    for i in set(obj.members):
      self.insert('account_group_users', {'group_name':one(obj.name),'email':i})

def RealMain(argv, data=None):
  os.environ['LC_ALL'] = 'C'
  options, args = parser.parse_args(argv[1:])

  srv = GetRpcServer(options)
  backup = Proxy(BackupService_Stub(srv))
  db = PgSQL.connect(database=options.dbname,
                     client_encoding="utf-8",
                     unicode_results=1)
  db.cursor().execute("set client_encoding to unicode")

  store = LocalStore(db)

  print 'BEGIN BACKUP'
  for kind_name in KINDS:
    sys.stdout.write('\n')
    cnt = 0
    last_key = ''

    while True:
      sys.stdout.write('\r%-18s ... ' % kind_name)
      r = NextChunkRequest()
      r.kind = kind_name
      r.last_key = last_key
      
      r = backup.NextChunk(r)
      if not r.entity:
        break

      for entity in r.entity:
        cnt += 1
        sys.stdout.write('\r%-18s ... %5d ' % (kind_name, cnt))

        o = parse_dom(parseString(
          '<?xml version="1.0" encoding="utf-8"?>'
          '<root xmlns:gd="http://www.google.com/">'
          '%s'
          '</root>'
          % entity.xml))
        getattr(store, 'save_%s' % kind_name)(entity, o)
        last_key = entity.key
      db.commit()

  sys.stdout.write('\n')
  print 'BACKUP DONE'
  db.commit()
  db.close()


def main():
  try:
    RealMain(sys.argv)
  except KeyboardInterrupt:
    print
    print "Interrupted."
    sys.exit(1)


if __name__ == "__main__":
  main()
