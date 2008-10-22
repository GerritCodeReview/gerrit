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

"""Info about the users

This requires Django 0.97.pre.
"""

import datetime
import logging

from django import forms
from django import http
from django.utils import simplejson

import models
import views
import fields
from view_util import *

def respond_json(obj):
  data = simplejson.dumps(obj)
  return http.HttpResponse(data,
      mimetype="text/javascript; charset=utf-8")

def get_user_info(request):
  """check if the requested user exists
  
  Parameters:
    id: the user's email address or the group name
    allow_users: whether users are allowed, true by default
    allow_groups: whether groups are allowed, true by default

  Result:
  If user/group not found:
    null
  If user found: map of
    type: "user"
    key: a unique key for this user
    email: their email address
    real_name: the user's real name
  If group found: map of
    type: "group"
    key: a unique key for this group
    name: the name of the group

  If users or groups are not allowed, then null will be returned
  even if there does happen to be a user or group with that name
  """
  id = request.REQUEST.get("id", None)
  if not id:
    return http.HttpResponseServerError("id not provided")
  allow_users = request.REQUEST.get("allow_users", "true")
  allow_groups = request.REQUEST.get("allow_groups", "true")
  if allow_users != "false":
    account = models.Account.get_account_for_email(id)
    if account:
      return respond_json({
          'allow_users': allow_users,
          'allow_groups': allow_groups,
          'type': 'user',
          'key': "user/" + account.email,
          'email': account.email,
          'real_name': account.real_name,
          })
  if allow_groups != "false":
    group = models.AccountGroup.get_group_for_name(id)
    if group:
      return respond_json({
          'allow_users': allow_users,
          'allow_groups': allow_groups,
          'type': 'group',
          'key': "group/" + str(group.key()),
          'name': group.name,
          })
  return respond_json(None)

@admin_required
def admin_people_info(request):
  """/admin/people_info"""
  op = request.REQUEST.get("op", None)
  if op == "get_user_info":
    return get_user_info(request);
  return http.HttpResponseServerError("op=" + str(op))

@admin_required
def admin_users(request):
  """/admin/users - list of all users"""
  accounts = models.Account.get_all_accounts()
  return respond(request, 'admin_users.html', {'users': accounts})

def _get_groups_for_account(account):
  return models.gql(models.AccountGroup,
                    'WHERE members = :1',
                    account.user).fetch(models.FETCH_MAX)

def _set_groups_for_account(account, groups):
  u = account.user
  existing = _get_groups_for_account(account)
  # remove from the ones that don't have it any more
  group_keys = [g.key() for g in groups]
  for g in existing:
    if g.key() not in group_keys:
      g.members.remove(u)
      g.put()
  # add to the ones that didn't have it before
  existing_keys = [g.key() for g in existing]
  for g in groups:
    if g.key() not in existing_keys:
      g.members.append(u)
      g.put()
  
class AdminUserForm(BaseForm):
  _template = 'admin_user.html'

  dest = forms.fields.CharField(widget=forms.widgets.HiddenInput)

  # user info
  real_name = forms.CharField(max_length=60)
  preferred_email = forms.EmailField(required=False)
  mailing_address = forms.CharField(required=False,
                          max_length=150,
                          widget=forms.Textarea(attrs={'rows': 4, 'cols': 60}))
  mailing_address_country = forms.CharField(max_length=30, required=False)
  phone_number = forms.CharField(max_length=30, required=False)
  fax_number = forms.CharField(max_length=30, required=False)

  # groups
  groups = fields.UserGroupField(allow_users=False, allow_groups=True)

  # cla
  cla_verified = forms.fields.BooleanField(required=False)
  cla_comments = forms.CharField(required=False,
                          max_length=300,
                          widget=forms.Textarea(attrs={'rows': 6, 'cols': 60}))

  @classmethod
  def _init(cls, state):
    account = state['account']
    dest = state['dest']
    return {
        'initial': {
            'dest': dest,
            'real_name': account.real_name,
            'preferred_email': account.preferred_email,
            'mailing_address': account.mailing_address,
            'mailing_address_country': account.mailing_address_country,
            'phone_number': account.phone_number,
            'fax_number': account.fax_number,
            'groups': _get_groups_for_account(account),
            'cla_verified': account.cla_verified,
            'cla_verified_by': account.cla_verified_by,
            'cla_verified_timestamp': account.cla_verified_timestamp,
            'individual_cla_version': account.individual_cla_version,
            'individual_cla_timestamp': account.individual_cla_timestamp,
            'cla_comments': account.cla_comments,
          },
        }

  def _save(self, cd, state):
    account = state['account']
    account.real_name = cd['real_name'].strip()
    if cd['preferred_email']:
      account.preferred_email = cd['preferred_email']
    account.mailing_address = cd['mailing_address']
    account.mailing_address_country = cd['mailing_address_country']
    account.phone_number = cd['phone_number']
    account.fax_number = cd['fax_number']
    if account.cla_verified != cd['cla_verified']:
      account.cla_verified = cd['cla_verified']
      account.cla_verified_by = models.Account.current_user_account.user
    if cd['cla_verified'] and account.cla_verified_timestamp is None:
      account.cla_verified_timestamp = datetime.datetime.utcnow()
    account.cla_comments = cd['cla_comments']
    account.put()
    _set_groups_for_account(account, 
                            fields.UserGroupField.get_groups(cd['groups']))
    if self.is_valid():
      return HttpResponseRedirect(cd['dest'])


@admin_required
def admin_user(request, email):
  """/admin/user/email@address.com - edit this user"""
  account = models.Account.get_account_for_email(email)
  referrer = request.META.get('HTTP_REFERER', '/admin/users')
  state = {
      'account': account,
      'dest': referrer,
    }
  return process_form(request, AdminUserForm, state, None, {'email':email})
  
@admin_required
def admin_groups(request):
  """/admin/groups - list of all groups"""
  models.AccountGroup.create_groups()
  groups = models.AccountGroup.get_all_groups()
  return respond(request, 'admin_groups.html', {'groups': groups})

class AdminGroupForm(BaseForm):
  _template = 'admin_group.html'

  name = forms.CharField(max_length=30)
  comment = forms.CharField(required=False,
                            max_length=10000,
                            widget=forms.Textarea(attrs={'cols': 60}))
  members = fields.UserGroupField(allow_users=True, allow_groups=False)

  @classmethod
  def _init(cls, group):
     who = group.members
     who = fields.UserGroupField.field_value_for_keys(users = who)
     return {'initial': {'name': group.name,
             'comment': group.comment,
             'members': who
            }}

  def _save(self, cd, group):
    new_name = cd['name'].strip()
    if group.is_auto_group and group.name != new_name:
      self.errors['name'] = ['Name can not be changed']

    # TODO(joeo): Big race condition here.
    #
    in_use = models.AccountGroup.get_group_for_name(new_name)
    if in_use and group.key() != in_use.key():
      self.errors['name'] = ['Name is already in use']

    if self.is_valid():
      group.name = new_name
      group.comment = cd['comment']
      group.members = fields.UserGroupField.get_users(cd['members'])
      group.put()

@admin_required
def admin_group(request, name):
  """/admin/group/group - edit this group"""
  group = models.AccountGroup.get_group_for_name(name)
  def done():
    return HttpResponseRedirect('/admin/groups')
  return process_form(request, AdminGroupForm, group, done,
                      {'group': group,
                       'del_url': '/admin/group_delete/%s' % group.name,
                      })
  
class AdminNewGroupForm(AdminGroupForm):
  @classmethod
  def _init(cls, group):
     return {'initial': {'name': '', 'comment': ''}}

  def _save(self, cd, group):
    new_name = cd['name'].strip()

    # TODO(joeo): Big race condition here.
    #
    if models.AccountGroup.get_group_for_name(new_name):
      self.errors['name'] = ['Name is already in use']

    if self.is_valid():
      who = fields.UserGroupField.get_users(cd['members'])
      group = models.AccountGroup(name = new_name,
                                  comment = cd['comment'],
                                  members = who)
      group.put()

@admin_required
def admin_group_new(request):
  """/admin/group/GROUP - add a new group"""
  def done():
    return HttpResponseRedirect('/admin/groups')
  return process_form(request, AdminNewGroupForm, [], done)

@gae_admin_required
@xsrf_required
def admin_group_delete(request, name):
  """/admin/group_delete/GROUP - add a new group"""
  group = models.AccountGroup.get_group_for_name(name)
  assert group
  group.remove()
  return HttpResponseRedirect('/admin/groups')


