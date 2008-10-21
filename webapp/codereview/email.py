# Copyright 2008 The Android Open Source Project
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

import logging

from google.appengine.ext import db
from google.appengine.api import mail
from google.appengine.api import users

import django.template

import models
import email

def get_default_sender():
  return models.Settings.get_settings().from_email

def _encode_safely(s):
  """Helper to turn a unicode string into 8-bit bytes."""
  if isinstance(s, unicode):
    s = s.encode('utf-8')
  return s

def _to_email_string(obj):
  if isinstance(obj, str):
    return obj
  elif isinstance(obj, unicode):
    return obj.encode('utf-8')
  elif isinstance(obj, db.Email):
    account = models.Account.get_account_for_email(obj)
  elif isinstance(obj, users.User):
    account = models.Account.get_account_for_user(obj)
  elif isinstance(obj, models.Account):
    account = obj
  if account:
    result = account.get_email_formatted()
  else:
    result = str(email)
  return _encode_safely(result)

def send(sender, to, subject, template, template_args):
  """Sends an email based on a template.

  All email address parameters can be: strings, unicode, db.Email users.User
  or models.Account objects.

  Args:
    sender:   The From address.  Null if it should be sent from the role acct.
    to:       An email address or a list of email address to be in the To field.
    subject:  The subject line.
    template: The name of the template file to use from the mail dir.
    template_args:  A map of args to be pasaed to the template
  """
  if not sender:
    sender_string = get_default_sender()
    if not sender_string:
      logging.warn('not sending email because there is no from address')
      return 'not sending email because there is no from address'
  else:
    sender_string = _to_email_string(sender)
  to_string = [_to_email_string(e) for e in to]
  body = django.template.loader.render_to_string(template, template_args)
  mail.send_mail(sender=sender_string, to=to_string, subject=subject, body=body)

def make_change_subject(change):
  subject = "(%s) %s" % (change.dest_project.name, change.subject)
  if change.message_set.count(1) > 0:
    subject = 'Re: ' + subject
  return subject

def send_change_message(request, change, template, template_args):
  to_users = set([change.owner] + change.reviewers + change.cc)
  subject = make_change_subject(change)
  args = {
      'url': request.build_absolute_uri('/%s' % change.key().id()),
    }
  if template_args:
    args.update(template_args)
  email.send(request.user, to_users, subject, template, args)


