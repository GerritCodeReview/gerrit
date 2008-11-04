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

"""Project related views and functions.

This requires Django 0.97.pre.
"""


# Python imports
import os
import cgi
import random
import re
import logging
import binascii
import datetime
import urllib
import md5
from xml.etree import ElementTree
from cStringIO import StringIO

# AppEngine imports
from google.appengine.api import mail
from google.appengine.api import memcache
from google.appengine.api import users
from google.appengine.api import urlfetch
from google.appengine.ext import db
from google.appengine.ext.db import djangoforms

# Django imports
# TODO(guido): Don't import classes/functions directly.
from django import http
from django import forms
from django.http import HttpResponse, HttpResponseRedirect
from django.http import HttpResponseForbidden, HttpResponseNotFound
from django.shortcuts import render_to_response
from django.utils import simplejson
from django.forms import formsets

# Local imports
import models
import engine
import library
import patching
import fields
import views
from view_util import *

## Project CRUD ##

def _assert_project_and_owner(request, project):
  "If the current user is not an owner of this project or an admin, give a 404."
  if not project:
    raise http.Http404()
  if not request.user or not ((project.key() in [
          p.key() for p in request.projects_owned_by_user])
      or request.user_is_admin):
    raise http.Http404()

@project_owner_or_admin_required
def project_list(request):
  """/admin/projects - list of all projects"""
  if request.user_is_admin:
    projects = models.Project.get_all_projects()
  else:
    projects = models.Project.projects_owned_by_user(request.user)
    logging.info("project_list projects=" + str(projects))
    if not projects:
      raise http.Http404()
  return respond(request, 'admin_projects.html', {'projects': projects})

def _keys_for(values):
  return [x.key() for x in values]

def _users_to_accounts(users):
  return map(models.Account.get_account_for_user, users)

def _combine_users_and_groups(users, groupKeys):
  return models.AccountGroup.get(groupKeys) + _users_to_accounts(users)

def _field_to_approval_right(cleaned):
  result = models.ApprovalRight()
  result.files = cleaned["files"]
  (result.approvers_users,result.approvers_groups
      ) = fields.UserGroupField.get_user_and_group_keys(cleaned["approvers"])
  (result.verifiers_users,result.verifiers_groups
      ) = fields.UserGroupField.get_user_and_group_keys(cleaned["verifiers"])
  (result.submitters_users,result.submitters_groups
      ) = fields.UserGroupField.get_user_and_group_keys(cleaned["submitters"])
  result.put()
  return result

def _approval_right_to_field(key):
  val = models.ApprovalRight.get(key)
  if not val:
    raise KeyError("no ApprovalRight for key: " + str(key))
  return {
      'files': val.files,
      'approvers': _combine_users_and_groups(val.approvers_users,
                      val.approvers_groups),
      'verifiers': _combine_users_and_groups(val.verifiers_users,
                      val.verifiers_groups),
      'submitters': _combine_users_and_groups(val.submitters_users,
                      val.submitters_groups),
    }

class AdminProjectForm(BaseForm):
  _template = 'admin_project.html'

  name = forms.CharField()
  comment = forms.CharField(required=False,
                            max_length=10000,
                            widget=forms.Textarea(attrs={'cols': 60}))
  owners = fields.UserGroupField(label='Project Lead(s)')
  code_reviews = fields.ApproversField()

  @classmethod
  def _init(cls, project):
     owners = fields.UserGroupField.field_value_for_keys(
                project.owners_users,
                project.owners_groups),
     return {'initial': {'name': project.name,
             'comment': project.comment,
             'owners': owners,
             'code_reviews': map(_approval_right_to_field,
                                 project.code_reviews),
            }}

  def _save(self, cd, project):
    new_name = cd['name'].strip()

    # TODO(joeo): Big race condition here.
    #
    in_use = models.Project.get_project_for_name(new_name)
    if in_use and project.key() != in_use.key():
      self.errors['name'] = ['Name is already in use']

    if self.is_valid():
      project.name = new_name
      project.comment = cd['comment']
      project.owners_users, project.owners_groups = \
        fields.UserGroupField.get_user_and_group_keys(cd['owners'])
      project.set_code_reviews(_keys_for(map(_field_to_approval_right,
                                         cd['code_reviews'])))
      project.put()

def project_edit(request, name):
  """/admin/project/project - edit this project"""
  project = models.Project.get_project_for_name(name)
  _assert_project_and_owner(request, project)
  def done():
    return HttpResponseRedirect('/admin/projects')
  return process_form(request, AdminProjectForm, project, done,
                      {'project':project,
                       'del_url': '/admin/project_delete/%s' % project.name,
                      })

class AdminNewProjectForm(AdminProjectForm):
  @classmethod
  def _init(cls, state):
    return {'initial': {'name': '', 'comment': ''}}

  def _save(self, cd, state):
    name = cd['name'].strip()

    # TODO(joeo): Big race condition here.
    #
    if models.Project.get_project_for_name(name):
      self.errors['name'] = ['Name is already in use']

    if self.is_valid():
      owners_users = fields.UserGroupField.get_users(cd['owners'])
      owners_groups = fields.UserGroupField.get_group_keys(cd['owners'])

      project = models.Project(name = name,
                               comment = cd['comment'],
                               owners_users = owners_users,
                               owners_groups = owners_groups)
      project.set_code_reviews(_keys_for(map(_field_to_approval_right,
                               cd['code_reviews'])))
      project.put()

@admin_required
def project_new(request):
  """/admin/project/GROUP - add a new project"""
  def done():
    return HttpResponseRedirect('/admin/projects')
  return process_form(request, AdminNewProjectForm, [], done)

@gae_admin_required
@xsrf_required
def project_delete(request, name):
  """/admin/project_delete/GROUP - add a new project"""
  project = models.Project.get_project_for_name(name)
  assert project
  project.remove()
  return HttpResponseRedirect('/admin/projects')


def _matches_file_pattern(pattern, files):
  """Returns whether the list of files matches the pattern"""
  for filename in files:
    if regex.match(filename):
      return True
  return False

def _is_leaf_pattern(pattern):
  """Returns whether the pattern ends with ..."""
  return not pattern.endswith("...")

def _split_rules_for_review(project):
  """Gets the approval file pattern rules for a project

  Returns:
    A tuple of:
      0 - A list of tuples of:
        0 - leaf pattern rules (rules of the form /.../xxx)
        1 - the ApprovalRight object
      1 - A list of tuples of:
        0 - directory pattern rules (rules of the form /xxx/...)
        1 - the ApprovalRight object
  """
  leaf_rules = []
  dir_rules = []

  for approval_right in project.get_code_reviews():
    for pattern in approval_right.files:
      if _is_leaf_pattern(pattern):
        leaf_rules.append((pattern, approval_right))
      else:
        dir_rules.append((pattern, approval_right))

  return (leaf_rules, dir_rules)

def _file_pattern_to_regex(pattern):
  if pattern.startswith('/'):
    pattern = pattern[1:]
  return re.compile('^%s$' % pattern.replace("...", ".*?"))

def _convert_pattern_to_regex(rules):
  return [(_file_pattern_to_regex(pattern),pattern,approval_right)
      for (pattern,approval_right) in rules]

def _flatten_rule_users(rules):
  flattened = {}
  def _flatten_approval_right(approval_right):
    if not flattened.has_key(approval_right):
      flattened[approval_right] = {
          'required': approval_right.required,
          'approvers': set([u.email() for u in approval_right.approvers()]),
          'verifiers': set([u.email() for u in approval_right.verifiers()]),
          'submitters': set([u.email() for u in approval_right.submitters()]),
        }
    return flattened[approval_right]
  return [(regex,pattern,_flatten_approval_right(approval_right))
      for (regex,pattern,approval_right) in rules]

def _split_files_for_review(project, files):
  """Return a mapping of files to the set of users that can approve or verify
  that file.

  Returns:
    A tuple of booleans, corresponding to (can_approve, can_verify).
  """
  result = {}

  (leaf_rules, dir_rules) = _split_rules_for_review(project)

  leaf_rules = _convert_pattern_to_regex(leaf_rules)
  dir_rules = _convert_pattern_to_regex(dir_rules)

  leaf_rules = _flatten_rule_users(leaf_rules)
  dir_rules = _flatten_rule_users(dir_rules)

  for file in files:
    approvers = []
    verifiers = []
    submitters = []

    # check leaf rules -- we want all of those that match
    for (regex,pattern,flat_approval_right) in leaf_rules:
      if _is_leaf_pattern(pattern):
        if regex.match(file):
          user_set = flat_approval_right['approvers']
          if not user_set in approvers:
            approvers.append(user_set)

          user_set = flat_approval_right['verifiers']
          if not user_set in verifiers:
            verifiers.append(user_set)

          user_set = flat_approval_right['submitters']
          if not user_set in submitters:
            submitters.append(user_set)

    # check dir rules -- we want all of those that match
    dir_rule_approvers = set()
    dir_rule_verifiers = set()
    dir_rule_submitters = set()

    for (regex,pattern,flat_approval_right) in reversed(dir_rules):
      if not _is_leaf_pattern(pattern):
        if regex.match(file):
          dir_rule_approvers |= flat_approval_right['approvers']
          dir_rule_verifiers |= flat_approval_right['verifiers']
          dir_rule_submitters |= flat_approval_right['submitters']
          if flat_approval_right['required']:
            break

    if not dir_rule_approvers in approvers:
      approvers.append(dir_rule_approvers)
    if not dir_rule_verifiers in verifiers:
      verifiers.append(dir_rule_verifiers)
    if not dir_rule_submitters in submitters:
      submitters.append(dir_rule_submitters)

    # save the result
    result[file] = {
          'approvers': approvers,
          'verifiers': verifiers,
          'submitters': submitters,
        }

  return result

def _check_users(possible, actual):
  """Return whether for each set in possible there is one entry from actual """
  for user_set in possible:
    inter = user_set.intersection(actual)
    if len(user_set.intersection(actual)) == 0:
      return False
  return True

def _match_users(possible, actual):
  """Return the users who have actually approved/verified a change"""
  matched = set()
  for user_set in possible:
    inter = user_set.intersection(actual)
    matched |= inter
  return matched

def ready_to_submit(branch, owner, reviewer_status, files, current_user):
  """Returns whether the supplied change is ready to submit.

  These are the rules for whether a change is considered ready to submit:

  1. If the project, repository or branch has code reviews turned off, then
     the change is ok.
  2. If all of the following are true, the change is ok:
    a. Either:
        i. Someone who is authorized to lgtm each file has done so.
        ii. For each file where the owner is the only approver in the list,
            there is at least one other person that has given a positive 
            score.
    b. No one who is authorized to say no has done so.

  If 'owner' can lgtm or verify a change, she is added to the respective list.
  """

  project = branch.project
  approved_cnt = 0
  verified_cnt = 0
  denied_cnt = 0
  submit_cnt = 0
  owner_auto_lgtm = False
  owner_auto_verify = False

  real_approvers = set()
  real_deniers = set()
  real_verifiers = set()
  lgtm_emails = set([u.email() for u in reviewer_status['lgtm']])
  reject_emails = set([u.email() for u in reviewer_status['reject']])
  verified_by_emails = set([u.email() for u in reviewer_status['verified_by']])
  owner_email = owner.email()

  schema_version = models.Settings.get_settings().schema_version
  if schema_version is None:
    schema_version = 0
  if schema_version == 0:
    user_can_submit = models.AccountGroup._is_in_cached_group(
      current_user,
      'submitters')
  else:
    user_can_submit = False

  files_to_approve = _split_files_for_review(project, files)
  if files_to_approve:
    for (file,user_sets) in files_to_approve.iteritems():
      # check the real approvals - the owner is checked in this list
      if _check_users(user_sets['approvers'], lgtm_emails):
        approved_cnt += 1
      if _check_users(user_sets['approvers'], reject_emails):
        denied_cnt += 1
      if _check_users(user_sets['verifiers'], verified_by_emails):
        verified_cnt += 1
      if schema_version > 0 \
         and current_user \
         and _check_users(user_sets['submitters'],
                          [current_user.email()]):
        submit_cnt += 1

      # check the owner auto-approve
      if _check_users(user_sets['approvers'], [owner_email]):
        owner_auto_lgtm = True
      if _check_users(user_sets['verifiers'], [owner_email]):
        owner_auto_verify = True

      real_approvers.update(_match_users(user_sets['approvers'], lgtm_emails))
      real_deniers.update(_match_users(user_sets['approvers'], reject_emails)) 
      real_verifiers.update(
                    _match_users(user_sets['verifiers'], verified_by_emails)) 


    require_review = False
    approved = ((approved_cnt == len(files_to_approve))
                or (owner_auto_lgtm
                    and ((not require_review)
                          or (len(reviewer_status['yes']) > 0
                               or len(reviewer_status['lgtm']) > 0))))
    verified = verified_cnt == len(files_to_approve) or owner_auto_verify
    denied = denied_cnt > 0
    if schema_version > 0:
      user_can_submit = submit_cnt == len(files_to_approve)
  else:
    approved = False
    verified = False
    denied = False

  return {
      'can_submit': user_can_submit \
                    and approved \
                    and verified \
                    and (not denied),
      'approved': approved,
      'denied': denied,
      'verified': verified,
      'owner_auto_lgtm': owner_auto_lgtm,
      'owner_auto_verify': owner_auto_verify,
      'real_approvers': real_approvers,
      'real_deniers': real_deniers,
      'real_verifiers': real_verifiers,
    }

def user_can_approve(user, change):
  """Returns whether the user can approve or verify this change.

  Args:
    user:   The user to test (a User object)
    branch: The branch that this change is in (a Branch object)
    files:  A list of files in question

  Returns:
    A map of 'approve' and 'verify' to booleans of whether the user
    can approve or verify at least one of these files.
  """
  # pick the files
  patchsets = list(change.patchset_set.order('id'))
  if not patchsets:
    return (False, False)
  files = patchsets[-1].filenames

  branch = change.dest_branch
  project = branch.project

  files_to_approve = _split_files_for_review(project, files)
  if not files_to_approve:
    return (False, False)

  email = user.email()
  can_approve = False
  can_verify = False
  for (file,user_sets) in files_to_approve.iteritems(): 
    for user_set in user_sets["approvers"]:
      if email in user_set:
        can_approve = True
    for user_set in user_sets["verifiers"]:
      if email in user_set:
        can_verify = True
    if can_approve and can_verify:
      # short circuit
      return (True, True)

  return (can_approve, can_verify)
