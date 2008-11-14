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

"""Custom fields and widgets for Gerrit.

This requires Django 0.97.pre.
"""


### Imports ###

import string
import logging

import django.forms.widgets
import django.forms.fields
from django import forms
from django.utils import encoding
from django.utils import safestring
from django.forms import util
from django.utils import html
from django.utils import simplejson
from google.appengine.ext import db

import models

def person_to_dict(v):
  entry = {}
  if isinstance(v, models.Account):
    entry["type"] = "user"
    entry["key"] = "user/" + v.email
    entry["email"] = v.email
    entry["real_name"] = v.real_name
    entry["sort_key"] = "2/" + unicode(v.user)
  elif isinstance(v, models.AccountGroup):
    entry["type"] = "group"
    entry["key"] = "group/" + str(v.key())
    entry["name"] = v.name
    entry["sort_key"] = "1/" + unicode(v.name)
  else:
    raise AssertionError("bad value: " + str(v) + " type: " + str(type(v)))
  return entry

def people_to_dicts(value, sorted):
  data = []
  for v in value:
    if isinstance(v, list):
      data.extend(people_to_dicts(v))
    elif v:
      data.append(person_to_dict(v))
  if sorted:
    data.sort(lambda a,b: cmp(a["sort_key"], b["sort_key"]))
  return data


### User/Group Field ###

class UserGroupWidget(django.forms.widgets.Widget):
  """The widget that is used with UserGroupField."""
  def __init__(self, allow_users=True, allow_groups=True, sorted=True,
                attrs=None):
    self.attrs = {'cols': '40', 'rows': '10'}
    self.allow_users = allow_users
    self.allow_groups = allow_groups
    self.read_only = None
    self.sorted = sorted
    if attrs:
      self.attrs.update(attrs)

  def render(self, name, value, attrs=None):
    if value is None:
      value = []
    read_only = self.read_only
    if read_only is None:
      read_only = []
    return safestring.mark_safe(
        u"""
        <div id="%(name)s_mom"></div>
        <script>
          UserGroupField_insertField(document.getElementById('%(name)s_mom'),
              '%(name)s', %(allow_users)s, %(allow_groups)s, %(initial)s,
              %(read_only)s);
        </script>
        """ % { "name":name,
                "initial":self._render_initial_js(value),
                "read_only":self._render_initial_js(read_only),
                "allow_users": ("true" if self.allow_users else "false"),
                "allow_groups": ("true" if self.allow_groups else "false"),
              })

  def _render_initial_js(self, value):
    data = people_to_dicts(value, self.sorted)
    return "[%s]" % ','.join(map(simplejson.dumps, data))

  def value_from_datadict(self, data, files, name):
    return data.getlist(name + "_keys")


class UserGroupField(django.forms.fields.Field):
  """A Field that picks a list of users and groups."""

  def __init__(self, *args, **kwargs):
    self.allow_users = kwargs.pop("allow_users", True)
    self.allow_groups = kwargs.pop("allow_groups", True)
    self.sorted = kwargs.pop("sorted", True)
    self.widget = UserGroupWidget(self.allow_users, self.allow_groups,
        self.sorted)
    super(UserGroupField, self).__init__(*args, **kwargs)

  def clean(self, data, initial=None):
    def get_correct_model(key):
      (type,id) = key.split("/", 1)
      if id:
        try:
          if type == "user":
            return models.Account.get_account_for_email(id)
          elif type == "group":
            return models.AccountGroup.get(id)
        except db.BadKeyError, v:
          pass
      raise forms.ValidationError("invalid key")
    keys = data
    result = [get_correct_model(key) for key in keys]
    super(UserGroupField, self).clean(initial or result)
    return result

  @classmethod
  def get_users(cls, cleaned):
    """Returns the users, given the cleaned data from the form.

    e.g.
    model_obj.usrs = fields.UserGroupField.get_users(form.cleaned_data['field'])
    """
    return [x.user for x in cleaned if isinstance(x, models.Account)]

  @classmethod
  def get_groups(cls, cleaned):
    """Returns the groups, given the cleaned data from the form.

    e.g.
    groups = fields.UserGroupField.get_users(form.cleaned_data['field'])
    """
    return [x for x in cleaned if isinstance(x, models.AccountGroup)]

  @classmethod
  def get_group_keys(cls, cleaned):
    """Returns keys for the groups, given the cleaned data from the form.

    e.g.
    groups = fields.UserGroupField.get_users(form.cleaned_data['field'])
    """
    return [x.key() for x in cleaned if isinstance(x, models.AccountGroup)]

  @classmethod
  def get_user_and_group_keys(cls, cleaned):
    """Returns the users and the groups for the cleaned data from the form.

    e.g.
    (model_obj.users,model_obj.groups
        ) = fields.UserGroupField.get_user_and_group_keys(
        form.cleaned_data['field'])
    """
    return (UserGroupField.get_users(cleaned),
            UserGroupField.get_group_keys(cleaned))

  @classmethod
  def field_value_for_keys(cls, users=[], groups=[]):
    """Return the value suitable for this field from a list keys.

    e.g.
    form_initial_values['field'] = fields.UserGroupField.field_value_for_keys(
              users, group_keys)
    """
    return ([models.AccountGroup.get(k) for k in groups]
          + [models.Account.get_account_for_user(u) for u in users])
  

### Approvers Field ###

class ApproversWidget(django.forms.widgets.Widget):
  """The widget for ApproversField"""

  def __init__(self, allow_users=True, allow_groups=True, attrs=None,
        approvers=None, verifiers=None, submitters=None):
    self.attrs = {'cols': '40', 'rows': '10'}
    if attrs:
      self.attrs.update(attrs)
    self.approvers = approvers or UserGroupWidget();
    self.verifiers = verifiers or UserGroupWidget();
    self.submitters = submitters or UserGroupWidget();

  def render(self, name, value, attrs=None):
    if value is None:
      value = []
    styles = self.attrs.get("styles", {})
    return safestring.mark_safe(
        u"""
        <div id="%(name)s_mom"></div>
        <script>
          ApproversField_insertField('%(name)s_mom', '%(name)s', %(initial)s,
              %(styles)s);
        </script>
        """ % {
          "name": name,
          "initial": self._render_initial_js(name, value),
          "styles": simplejson.dumps(styles),
        })


  def _render_initial_js(self, name, value):
    data = []
    index = 0
    for v in value:
      # BEGIN DEBUGGING
      key = "initial_%d" % index
      files = v["files"]
      bad_files = v.get("bad_files", [])
      approvers = v["approvers"]
      verifiers = v["verifiers"]
      submitters = v["submitters"]
      # END DEBUGGING
      entry = {}
      entry["key"] = "initial_%d" % index
      entry["files"] = encoding.force_unicode("\n".join(files))
      entry["bad_files"] = map(encoding.force_unicode, bad_files)
      entry["approvers"] = people_to_dicts(approvers)
      entry["verifiers"] = people_to_dicts(verifiers)
      entry["submitters"] = people_to_dicts(submitters)
      data.append(entry)
      index = index + 1
    rows = []
    for entry in data:
      rows.append(simplejson.dumps(entry))
    return "[%s]" % ','.join(rows)
    

  def value_from_datadict(self, data, files, name):
    result = []
    keys = data.getlist(name + "_keys")
    for key in keys:
      field_key = "%s_%s" % (name, key)
      files = filter(string.strip, data.get(field_key + "_files").splitlines())
      bad_files = []
      for f in files:
        if not models.ApprovalRight.validate_file(f):
          err = True
          bad_files.append(f)
      approvers = self.approvers.value_from_datadict(data, files,
          field_key + "_approvers")
      verifiers = self.verifiers.value_from_datadict(data, files,
          field_key + "_verifiers")
      submitters = self.submitters.value_from_datadict(data, files,
          field_key + "_submitters")
      result.append({
              "key": key,
              "files": files,
              "bad_files": bad_files,
              "approvers": approvers,
              "verifiers": verifiers,
              "submitters": submitters,
          })
    return result


class ApproversField(django.forms.fields.Field):
  """A Field to pick which users/groups can edit which field"""
  approvers = UserGroupField();
  verifiers = UserGroupField();
  submitters = UserGroupField();
  widget = ApproversWidget(
      approvers=approvers.widget,
      verifiers=verifiers.widget,
      submitters=submitters.widget,
      attrs={"styles": {
        "approval": "approval"
      }})

  def __init__(self, *args, **kwargs):
    super(ApproversField, self).__init__(*args, **kwargs)

  def clean(self, data, initial=None):
    result = []
    err = False
    for d in data:
      files = d["files"]
      if len(d["bad_files"]) > 0:
        err = True
      approvers = self.approvers.clean(d["approvers"])
      verifiers = self.verifiers.clean(d["verifiers"])
      submitters = self.submitters.clean(d["submitters"])
      result.append({
          "files": files,
          "approvers": approvers,
          "verifiers": verifiers,
          "submitters": submitters,
          })
    super(ApproversField, self).clean(initial or result)
    if err:
        raise forms.ValidationError("invalid files")
    return result

### Project field ###

class ProjectSelectWidget(django.forms.widgets.Widget):
  """A widget that lets a user pick a set of projects."""
  def __init__(self, attrs=None):
    super(ProjectSelectWidget, self).__init__(attrs)
    if attrs:
      self.attrs.update(attrs)

  def render(self, name, value, attrs=None):
    if value is None:
      value = []
    project_list = [{'name': p.name, 'key': str(p.key())}
                    for p in models.Project.get_all_projects()]
    return safestring.mark_safe(
        u"""
        <div id="%(name)s_mom"></div>
        <script>
          ProjectField_insertField(document.getElementById('%(name)s_mom'),
              '%(name)s', %(project_list)s, %(initial)s);
        </script>
        """ % { "name": name,
                "project_list": self._render_js_list(project_list),
                "initial": self._render_js_list([str(v) for v in value]),
              })

  def _render_js_list(self, value):
    return "[%s]" % ','.join(map(simplejson.dumps, value))

  def value_from_datadict(self, data, files, name):
    return set([v.strip()
                for v in data.getlist(name)
                if len(v.strip()) > 0
                   and v.strip() not in ('--', '--==', 'null')])

class ProjectSelectField(django.forms.fields.Field):
  """A Field that lets a user pick a set of projects."""

  def __init__(self, *args, **kwargs):
    self.widget = ProjectSelectWidget()
    super(ProjectSelectField, self).__init__(*args, **kwargs)

  def clean(self, data, initial=None):
    objects = models.Project.get(data)
    result = [o.key() for o in objects if o]
    super(ProjectSelectField, self).clean(initial or result)
    return result

### Read only text widget ###

class ReadOnlyTextWidget(django.forms.widgets.Widget):
  """Just some text"""
  def __init__(self, attrs=None):
    self.attrs = {}
    if attrs:
      self.attrs.update(attrs)

  def render(self, name, value, attrs=None):
    if value is None:
      value = ''
    return safestring.mark_safe(
        "%(v)s<input type='hidden' name='%(n)s' value='%(v)s' />" % {
        'n': name,
        'v': html.escape(value),
      })

  def value_from_datadict(self, data, files, name):
    return data[name]



