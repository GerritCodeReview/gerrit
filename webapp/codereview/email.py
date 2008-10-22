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

import email
import library
import models

def get_default_sender():
  return models.Settings.get_settings().from_email

def _encode_safely(s):
  """Helper to turn a unicode string into 8-bit bytes."""
  if isinstance(s, unicode):
    s = s.encode('utf-8')
  return s

def _to_email_string(obj):
  if not obj:
    return None
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

def _make_to_strings(to):
  return [x for x in [_to_email_string(e) for e in to] if x]


def send(sender, to, subject, template, template_args):
  """Sends an email based on a template.

  All email address parameters can be: strings, unicode, db.Email users.User
  or models.Account objects.

  Returns a Message object, suitable for keeping in the db.

  Args:
    sender:   The From address.  Null if it should be sent from the role acct.
    to:       An email address or a list of email address to be in the To field.
    subject:  The subject line.
    template: The name of the template file to use from the mail dir.
    template_args:  A map of args to be pasaed to the template
  """
  sender_string = _to_email_string(sender)
  if not sender_string:
    return 'no from address'
  to_strings = _make_to_strings(to)
  if not to_strings:
    return 'no to addresses'
  body = django.template.loader.render_to_string(template, template_args)
  mail.send_mail(sender=sender_string, to=to_strings, subject=subject,
                  body=body)


def make_change_subject(change):
  subject = "(%s) %s" % (change.dest_project.name, change.subject)
  if change.message_set.count(1) > 0:
    subject = 'Re: ' + subject
  return subject

def send_change_message(request, change, template, template_args,
                        sender, send_email=True, email_template=None):
  # sender
  if not sender:
    sender = get_default_sender()
  sender_string = _to_email_string(sender)

  # to
  to_users = set([change.owner] + change.reviewers + change.cc)
  if sender in to_users:
    to_users.remove(sender)
  to_strings = _make_to_strings(to_users)
  to_emails = [db.Email(s) for s in to_strings]

  # subject
  subject = make_change_subject(change)

  # body
  uri = library.change_url(change)
  if not email_template:
    email_template = template
  body = django.template.loader.render_to_string(email_template, template_args)

  # don't send emails without all of these fields
  if not sender_string or not to_strings or not subject or not body:
    logging.error("sender_string=%s to_strings=%s subject=%s body=%s" %
        (sender_string, to_strings, subject, body))
    return None

  # send the email
  if send_email:
    message_body = "%s\n--\n%s\n" % (body, uri)
    mail.send_mail(sender=sender_string, to=to_strings, subject=subject,
                    body=message_body)
  
  # make and return the email
  if email_template != template:
    body = django.template.loader.render_to_string(
                                          template, template_args)
  msg = models.Message(change=change,
                       subject=subject,
                       sender=sender_string,
                       recipients=to_emails,
                       text=db.Text(body),
                       parent=change)
  return msg


