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

"""Views for the settings section of Gerrit.

This requires Django 0.97.pre.
"""

import datetime
import logging
import urllib

from google.appengine.api import memcache

from django import forms
from django import http

import fields
import models
import views
from view_util import *


### Users settings page -- /settings ###

def validate_real_name(real_name):
  """Returns None if real_name is fine and an error message otherwise."""
  if not real_name:
    return 'Name cannot be empty.'
  elif real_name == 'me':
    return 'Of course, you are what you are.  But \'me\' is for everyone.'
  elif '"' in real_name:
    return 'Name cannot contain the quotation mark (") character'
  else:
    return None


class SettingsForm(BaseForm):
  _template = 'settings.html'

  context = forms.IntegerField(widget = forms.Select(
                               choices = [(x, "%d lines" % x) \
                                          for x \
                                          in models.CONTEXT_CHOICES]
                               ),
                               label = 'Context')
  unclaimed_changes_projects = fields.ProjectSelectField()

  @classmethod
  def _init(cls, account):
    return {'initial': {
              'context': account.default_context,
              'unclaimed_changes_projects': account.unclaimed_changes_projects,
              }
            }

  def _save(self, cd, account):
    account.default_context = cd.get("context")
    account.unclaimed_changes_projects = cd.get("unclaimed_changes_projects")
    memcache.delete(views.unclaimed_project_memcache_key(account.user))
    account.put()

@login_required
def settings(request):
  account = models.Account.current_user_account
  def done():
    return http.HttpResponseRedirect('/mine')
  return process_form(request, SettingsForm, account, done)


### Welcome page -- /settings/welcome ###

class WelcomeForm1(BaseForm):
  _template = 'settings_welcome_1.html'

  dest = forms.fields.CharField(widget=forms.widgets.HiddenInput)
  real_name = forms.fields.CharField()

  @classmethod
  def _init(cls, state):
    account = state['account']
    if account.real_name_entered:
      real_name = account.real_name
    else:
      real_name = ""
    return {'initial': {
              'real_name': real_name,
              'dest': state['dest'],
            }
          }

  def _save(self, cd, state):
    account = state['account']
    account.real_name = cd['real_name']
    account.real_name_entered = True
    account.put()

    return _init_form(WelcomeForm2, cd)

class WelcomeForm2(BaseForm):
  _template = 'settings_welcome_2.html'

  cla = forms.fields.CharField(widget=forms.widgets.RadioSelect(choices=(
                            'none', 'individual', 'corporate')))
  dest = forms.fields.CharField(widget=forms.widgets.HiddenInput)

  @classmethod
  def _init(cls, state):
    return {'initial': {
              'dest': state['dest'],
              'cla': 'none',
            }
          }

  def _pre_verify(self, get, post):
    if not post.get('continue'):
      return _init_form(WelcomeForm1, post)

  def _save(self, cd, state):
    cla = cd['cla']
    if cla == 'none':
      account = state['account']
      account.welcomed = True
      account.put()
      return _init_form(WelcomeForm4, cd)
    elif cla == 'individual':
      return _init_form(WelcomeForm3Individual, cd)
    elif cla == 'corporate':
      return _init_form(WelcomeForm3Corporate, cd)


class WelcomeForm3Individual(BaseForm):
  _template = 'settings_welcome_3_individual.html'

  dest = forms.fields.CharField(widget=forms.widgets.HiddenInput)

  i_agree = forms.fields.CharField()

  mailing_address = forms.CharField(required=True,
                          max_length=150,
                          widget=forms.Textarea(attrs={'rows': 4, 'cols': 60}))
  mailing_address_country = forms.CharField(max_length=30, required=True)
  phone_number = forms.CharField(max_length=30, required=True)
  fax_number = forms.CharField(max_length=30, required=False)

  @classmethod
  def _init(cls, state):
    return {'initial': {
              'dest': state['dest'],
            }
          }

  def _pre_verify(self, get, post):
    if not post.get('continue'):
      return _init_form(WelcomeForm2, post)

  def _save(self, cd, state):
    if cd['i_agree'].lower() != 'i agree':
      self.errors['i_agree'] = 'You must agree to this license.'
      return
    account = state['account']
    # NOTE: just using 1 for now, future versions can come later
    account.individual_cla_version = 1
    account.cla_verified = True
    account.mailing_address = cd['mailing_address']
    account.mailing_address_country = cd['mailing_address_country']
    account.phone_number = cd['phone_number']
    account.fax_number = cd['fax_number']
    account.individual_cla_timestamp = datetime.datetime.utcnow()
    account.welcomed = True
    account.put()

    return _init_form(WelcomeForm4, cd)

class WelcomeForm3Corporate(BaseForm):
  _template = 'settings_welcome_3_corporate.html'

  dest = forms.fields.CharField(widget=forms.widgets.HiddenInput)

  @classmethod
  def _init(cls, state):
    return {'initial': {
              'dest': state['dest'],
            }
          }

  def _pre_verify(self, get, post):
    if not post.get('continue'):
      return _init_form(WelcomeForm2, post)

  def _save(self, cd, state):
    account = state['account']
    account.welcomed = True
    account.put()
  
    return _init_form(WelcomeForm4, cd)

class WelcomeForm4(BaseForm):
  _template = 'settings_welcome_4.html'

  dest = forms.fields.CharField(widget=forms.widgets.HiddenInput)

  @classmethod
  def _init(cls, state):
    return {'initial': {
              'dest': state['dest'],
            }
          }

  def _save(self, cd, state):
    return http.HttpResponseRedirect(cd['dest'])



_SETTINGS_STEPS = {
  '1':      WelcomeForm1,
  '2':      WelcomeForm2,
  '3ind':   WelcomeForm3Individual,
  '3corp':  WelcomeForm3Corporate,
  '4':      WelcomeForm4,
}

def _init_form(cls, post):
  state = {
      'dest': post['dest'],
      'account': models.Account.current_user_account,
    }
  kwargs = cls._init(state)
  # ok to pull this from post b/c it'll be
  # validated later when they submit
  kwargs['initial']['xsrf'] = post['xsrf']
  return cls(**kwargs)

@login_required
def settings_welcome(request):
  if request.method == 'GET':
    state = {
      'dest': request.GET.get('dest', '/'),
      'account': models.Account.current_user_account,
    }
    return process_form(request, WelcomeForm1, state, None)
  else:
    try:
      step = str(request.POST['step'])
      form_cls = _SETTINGS_STEPS[step]
    except KeyError:
      raise http.Http404 
    state = {
      'dest': request.POST['dest'],
      'account': models.Account.current_user_account,
    }
    params = {
      'post_url': '/settings/welcome?dest=%s' % urllib.quote(state['dest']),
    }
    return process_form(request, form_cls, state, None)

