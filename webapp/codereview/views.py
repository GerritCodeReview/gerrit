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

"""Views for Gerrit.

This requires Django 0.97.pre.
"""


### Imports ###


# Python imports
import os
import cgi
import random
import re
import logging
import binascii
import datetime
import hashlib
import zlib
from xml.etree import ElementTree
from cStringIO import StringIO

# AppEngine imports
from google.appengine.api import mail
from google.appengine.api import users
from google.appengine.ext import db
from google.appengine.ext.db import djangoforms

# Django imports
# TODO(guido): Don't import classes/functions directly.
from django import forms
from django import http
from django.http import HttpResponse, HttpResponseRedirect
from django.http import HttpResponseForbidden, HttpResponseNotFound
from django.shortcuts import render_to_response
import django.template
from django.utils import simplejson
from django.forms import formsets

# Local imports
import changetable
from memcache import Key as MemCacheKey
import models
import email
import engine
import library
import patching
import fields
import project
import git_models
from view_util import *


### Constants ###

MAX_ROWS = 1000

CHANGE_TABLE_FIELDS = (changetable.FIELD_ID,
          changetable.FIELD_SUBJECT,
          changetable.FIELD_OWNER,
          changetable.FIELD_REVIEWERS,
          changetable.FIELD_PROJECT,
#            changetable.FIELD_BRANCH,
          changetable.FIELD_MODIFIED,
         )

CHANGE_TABLE_FIELDS_NO_PROJECT = (changetable.FIELD_ID,
          changetable.FIELD_SUBJECT,
          changetable.FIELD_OWNER,
          changetable.FIELD_REVIEWERS,
#            changetable.FIELD_BRANCH,
          changetable.FIELD_MODIFIED,
         )

### Helper functions ###


def _random_bytes(n):
  """Helper returning a string of random bytes of given length."""
  return ''.join(map(chr, (random.randrange(256) for i in xrange(n))))


### Request handlers ###


def index(request):
  """/ - Show a list of patches."""
  if request.user is None:
    return all(request)
  else:
    return mine(request)

def hello(request):
  """/hello"""
  return HttpResponseRedirect('/settings/welcome?dest=/')
  #return respond(request, 'hello.html', {})

DEFAULT_LIMIT = 10

def all(request):
  """/all - Show a list of up to DEFAULT_LIMIT recent change."""
  offset = request.GET.get('offset')
  if offset:
    try:
      offset = int(offset)
    except:
      offset = 0
    else:
      offset = max(0, offset)
  else:
    offset = 0
  limit = request.GET.get('limit')
  if limit:
    try:
      limit = int(limit)
    except:
      limit = DEFAULT_LIMIT
    else:
      limit = max(1, min(limit, 100))
  else:
    limit = DEFAULT_LIMIT
  query = db.GqlQuery('SELECT * FROM Change '
                      'WHERE closed = FALSE ORDER BY modified DESC')
  # Fetch one more to see if there should be a 'next' link
  changes = query.fetch(limit+1, offset)
  more = bool(changes[limit:])
  if more:
    del changes[limit:]
  if more:
    next = '/all?offset=%d&limit=%d' % (offset+limit, limit)
  else:
    next = ''
  if offset > 0:
    prev = '/all?offset=%d&limit=%d' % (max(0, offset-limit), limit)
  else:
    prev = ''
  newest = ''
  if offset > limit:
    newest = '/all?limit=%d' % limit

  _optimize_draft_counts(changes)
  _prefetch_names(changes)

  table = changetable.ChangeTable(request.user, CHANGE_TABLE_FIELDS)
  table.add_section(None, changes)

  return respond(request, 'all.html', {
                    'changes': table.render(),
                    'limit': limit,
                    'newest': newest,
                    'prev': prev,
                    'next': next,
                    'first': offset+1,
                    'last': len(changes) > 1 and offset+len(changes) or None
                  })


def _optimize_draft_counts(changes):
  """Force _num_drafts to zero for changes that are known to have no drafts.

  Args:
    changes: list of model.Change instances.

  This inspects the drafts attribute of the current user's Account
  instance, and forces the draft count to zero of those changes in the
  list that aren't mentioned there.

  If there is no current user, all draft counts are forced to 0.
  """
  account = models.Account.current_user_account
  if account is None:
    change_ids = None
  else:
    change_ids = account.drafts
  for change in changes:
    if change_ids is None or change.key().id() not in change_ids:
      change._num_drafts = 0

def _prefetch_names(changes):
  for c in changes:
    library.prefetch_names([c.owner])
    library.prefetch_names(c.reviewers)
    library.prefetch_names(c.cc)


@login_required
def mine(request):
  """/mine - Show a list of changes created by the current user."""
  request.user_to_show = request.user
  return _show_user(request)

def all_unclaimed(request):
  flat_changes = models.gql(models.Change,
                          ' WHERE closed = FALSE'
                          '   AND claimed = FALSE'
                          ' ORDER BY dest_project DESC,'
                          '   modified DESC').fetch(1000)
  changes = changetable.ChangeTable(request.user,
      CHANGE_TABLE_FIELDS_NO_PROJECT)
  c_list = []
  last_project_key = None

  for c in flat_changes:
    k = c.dest_project.key()
    if k != last_project_key:
      if c_list:
        _optimize_draft_counts(c_list)
        _prefetch_names(c_list)
        changes.add_section(c_list[0].dest_project.name, c_list)
      last_project_key = k
      c_list = []
    c_list.append(c)

  if c_list:
    _optimize_draft_counts(c_list)
    _prefetch_names(c_list)
    changes.add_section(c_list[0].dest_project.name, c_list)
  vars = {
      'changes': changes.render(),
    }

  return respond(request, 'unclaimed.html', vars)

@login_required
def unclaimed(request):
  """/unclaimed - Show changes with no reviewer listed for
     user's selected projects.
  """
  changes = changetable.ChangeTable(request.user,
      CHANGE_TABLE_FIELDS_NO_PROJECT)

  for project in db.get(request.account.unclaimed_changes_projects):
    if project is None:
      continue
    c = models.gql(models.Change,
                          ' WHERE closed = FALSE'
                          '   AND claimed = FALSE'
                          '   AND dest_project = :dest_project'
                          ' ORDER BY modified DESC',
                          dest_project=project.key()).fetch(1000)
    if len(c) > 0:
      _optimize_draft_counts(c)
      _prefetch_names(c)
      changes.add_section(project.name, c)

  vars = {
      'changes': changes.render(),
    }

  return respond(request, 'unclaimed.html', vars)


@login_required
def starred(request):
  """/starred - Show a list of changes starred by the current user."""
  stars = models.Account.current_user_account.stars
  table = changetable.ChangeTable(request.user, CHANGE_TABLE_FIELDS)
  if stars:
    changes = [change for change in models.Change.get_by_id(stars)
                    if change is not None]
    _optimize_draft_counts(changes)
    _prefetch_names(changes)
    table.add_section(None, changes)
  return respond(request, 'starred.html', {'changes': table.render()})


@user_key_required
def show_user(request):
  """/user - Show the user's dashboard"""
  return _show_user(request)

def _respond_paged(request, template, new_pfx, old_pfx, vars):
  return respond(request, template, {
                 new_pfx + '_list': vars[old_pfx + '_list'],
                 new_pfx + '_opos': vars[old_pfx + '_opos'],
                 new_pfx + '_oend': vars[old_pfx + '_oend'],
                 new_pfx + '_prev': vars[old_pfx + '_prev'],
                 new_pfx + '_next': vars[old_pfx + '_next']
                 })

def _paginate(prefix, n, offset, q):
  list = q.fetch(n + 1, offset - 1)
  have = len(list)

  if have == n + 1:
    list = list[0:-1]
    have = n
    next = offset + n
    if next >= 1000:
      next = None
  else:
    next = None

  if offset == 0:
    prev = None
  else:
    prev = offset - n
    if prev < 0:
      prev = 0

  if next:
    last = next - 1
  else:
    last = offset + have - 1

  for i in list:
    i.paginate_row_type = prefix

  return {prefix + '_list': list,
          prefix + '_opos': offset,
          prefix + '_oend': last,
          prefix + '_prev': prev,
          prefix + '_next': next}


def _user_mine(user):
  r = models.gql(models.Change,
      'WHERE closed = FALSE AND owner = :1'
      ' ORDER BY modified DESC',
      user).fetch(1000)
  _optimize_draft_counts(r)
  _prefetch_names(r)
  return r

def _user_review(user):
  r = models.gql(models.Change,
      'WHERE closed = FALSE AND reviewers = :1'
      ' ORDER BY modified DESC',
      user.email()).fetch(1000)
  _optimize_draft_counts(r)
  _prefetch_names(r)
  return r

def _user_closed(user):
  r = models.gql(models.Change,
      'WHERE closed = TRUE AND owner = :1 AND modified > :2'
      ' ORDER BY modified DESC',
      user,
      datetime.datetime.now() - datetime.timedelta(days=7)
      ).fetch(15)
  _optimize_draft_counts(r)
  _prefetch_names(r)
  return r

def _show_user(request):
  user = request.user_to_show
  real_name = library.real_name(user)

  mine = _user_mine(user)
  review = _user_review(user)
  closed = _user_closed(user)

  changes = changetable.ChangeTable(request.user, CHANGE_TABLE_FIELDS)

  changes.add_section('Changes uploaded by %s' % real_name, mine)
  changes.add_section('Changes reviewable by %s' % real_name, review)
  changes.add_section('Recently closed changes', closed)
  
  vars = {
      'email': user.email(),
      'changes': changes.render(),
    }

  return respond(request, 'user.html', vars)


def _get_emails(form, label):
  """Helper to return the list of reviewers, or None for error."""
  emails = []
  raw_emails = form.cleaned_data.get(label)
  if raw_emails:
    for email in raw_emails.split(','):
      email = email.strip().lower()
      if email and email not in emails:
        try:
          email = db.Email(email)
          if email.count('@') != 1:
            raise db.BadValueError('Invalid email address: %s' % email)
          head, tail = email.split('@')
          if '.' not in tail:
            raise db.BadValueError('Invalid email address: %s' % email)
        except db.BadValueError, err:
          form.errors[label] = [unicode(err)]
          continue
        emails.append(email)
  return emails

def _prepare_show_patchset(user, patchset):
  if user:
    drafts = list(models.gql(models.Comment,
             'WHERE ANCESTOR IS :1'
             ' AND draft = TRUE'
             ' AND author = :2',
             patchset, user))
  else:
    drafts = []

  max_rows = 100
  if len(patchset.filenames) < max_rows:
    files = models.gql(models.Patch,
                       'WHERE patchset = :1 ORDER BY filename',
                        patchset).fetch(max_rows)
    patchset.n_comments = 0
    patchset.n_drafts = len(drafts)
    patchset.patches = files

    if drafts:
      p_bykey = dict()
      for p in patchset.patches:
        p_bykey[p.key()] = p
        p._num_drafts = 0
        patchset.n_comments += p.num_comments

      for d in drafts:
        if d.parent_key() in p_bykey:
          p = p_bykey[d.parent_key()]
          p._num_drafts += 1
    else:
      for p in patchset.patches:
          p._num_drafts = 0
  else:
    patchset.freaking_huge = True
    patchset.patches = []


def _restrict_lgtm(lgtm, can_approve):
  if not can_approve:
    if lgtm == 'lgtm':
      return 'yes'
    elif lgtm == 'reject':
      return 'no'
  return lgtm
    

def _restrict_verified(verified, can_verify):
  if can_verify:
    return verified
  else:
    return False

def _map_status(rs, real_approvers,
                    real_deniers,
                    real_verifiers,
                    known_owners,
                    known_approvers,
                    known_verifiers):
  email = rs.user.email()
  lgtm = _restrict_lgtm(rs.lgtm,
      (email in real_approvers) or (email in real_deniers))
  verified = _restrict_verified(rs.verified, email in real_verifiers)

  if email in known_owners:
    user_type = 'Project Lead'
  elif email in known_approvers:
    user_type = 'Approver'
  elif email in known_verifiers:
    user_type = 'Verifier'
  else:
    user_type = 'Contributor'

  return {
      'user': rs.user,
      'user_type' : user_type,
      'lgtm': lgtm,
      'verified': verified,
    }

@change_required
def show(request, form=None):
  """/<change> - Show a change."""
  change = request.change

  messages = list(change.message_set.order('date'))
  patchsets = list(change.patchset_set.order('id'))
  if not patchsets:
    return HttpResponse('No patchset available.')

  last_patchset = patchsets[-1]
  _prepare_show_patchset(request.user, last_patchset)

  if last_patchset.patches:
    first_patch = last_patchset.patches[0]
  else:
    first_patch = None

  dependencies_table = changetable.ChangeTable(request.user,
      CHANGE_TABLE_FIELDS_NO_PROJECT)
  depends_on = [ r.patchset.change
                 for r in last_patchset.revision.get_ancestors()
                 if r.patchset ]
  dependencies_table.add_section("Depends On", depends_on)
  needed_by  = [ r.patchset.change
                 for r in last_patchset.revision.get_children()
                 if r.patchset ]
  dependencies_table.add_section("Needed By", needed_by)

  # approvals
  review_status = change.get_review_status()
  reviewer_status = models.Change.get_reviewer_status(review_status)
  ready_to_submit = project.ready_to_submit(
      change.dest_branch,
      change.owner,
      reviewer_status,
      last_patchset.filenames,
      request.user)

  # if the owner can lgtm or verify, show her too
  author_status = {
      'lgtm': 'lgtm' if ready_to_submit['owner_auto_lgtm'] else 'abstain',
      'verified': ready_to_submit['owner_auto_verify']
    }

  show_dependencies = len(needed_by) > 0
  for c in depends_on:
    if not c.closed:
      show_dependencies = True
  if change.closed:
    show_dependencies = False

  can_submit = ready_to_submit['can_submit']
  if not last_patchset.complete:
    can_submit = False

  project_owners = set([u.email() for u in change.dest_project.leads()])
  project_approvers = set()
  project_verifiers = set()

  for ar in change.dest_project.get_code_reviews():
    for u in ar.approvers():
      project_approvers.add(u.email())
    for u in ar.verifiers():
      project_verifiers.add(u.email())

  real_approvers = ready_to_submit['real_approvers']
  real_deniers = ready_to_submit['real_deniers']
  real_verifiers = ready_to_submit['real_verifiers']

  real_review_status = [
      _map_status(rs,
                  real_approvers,
                  real_deniers,
                  real_verifiers,
                  project_owners,
                  project_approvers,
                  project_verifiers)
      for rs in review_status]

  show_submit_button = (not change.is_submitted) and can_submit
  show_more_options = change.user_can_edit(request.user)
  delete_url = '/%s/delete' % change.key().id()

  _prefetch_names([change])
  _prefetch_names(depends_on)
  _prefetch_names(needed_by)
  library.prefetch_names(map(lambda s: s.user, review_status))

  return respond(request, 'change.html', {
                  'change': change,
                  'ready_to_submit': can_submit,
                  'show_submit_button': show_submit_button,
                  'is_approved': ready_to_submit['approved'],
                  'is_rejected': ready_to_submit['denied'],
                  'is_verified': ready_to_submit['verified'],
                  'author_status': author_status,
                  'review_status': real_review_status,
                  'dependencies_table': dependencies_table.render(),
                  'show_dependencies': show_dependencies,
                  'patchsets': patchsets,
                  'messages': messages,
                  'last_patchset': last_patchset,
                  'first_patch': first_patch,
                  'reply_url': '/%s/publish' % change.key().id(),
                  'merge_url': '/%s/merge/%s' % (change.key().id(),
                                                 last_patchset.key().id()),
                  'show_more_options': show_more_options,
                  'delete_url': delete_url,
                })


@patchset_required
def ajax_patchset(request):
  """/<change>/ajax_patchset/<ps> - Format one patchset."""
  change = request.change
  patchset = request.patchset

  _prepare_show_patchset(request.user, patchset)
  return respond(request, 'patchset.html',
                 {'change' : request.change,
                  'patchset' : request.patchset
                 })


def revision_redirect(request, hash):
  """/r/<hash> - Redirect to a Change for this git revision, if we can."""
  hash = hash.lower()
  if len(hash) < 40:
    q = models.gql(models.RevisionId,
                   "WHERE id > :1 AND id < :2",
                   hash, hash.ljust(40, 'z'))
  else:
    q = models.gql(models.RevisionId, "WHERE id=:1", hash)
  revs = q.fetch(2)
  count = len(revs)

  if count == 1:
    rev_id = revs[0]
    if rev_id.patchset:
      return HttpResponseRedirect('/%s' % rev_id.patchset.change.key().id())
    else:
      return respond(request, 'change_revision_unknown.html', { 'hash': hash })

  if count > 0:
    return http.HttpResponseServerError("error 500: multiple matches for hash")

  if len(hash) < 40:
    q = models.gql(git_models.ReceivedBundle,
                   "WHERE contained_objects > :1"
                   "  AND contained_objects < :2",
                   hash, hash.ljust(40, 'z'))
  else:
    q = models.gql(git_models.ReceivedBundle,
                   "WHERE contained_objects=:1",
                   hash)
  rb = q.get()
  if rb:
    return respond(request, 'change_revision_uploading.html',
                   { 'hash': hash })
  else:
    return respond(request, 'change_revision_unknown.html',
                   { 'hash': hash },
                   status = 404)


@change_owner_required
@xsrf_required
def delete(request):
  """/<change>/delete - Delete a change.  There is no way back."""
  change = request.change

  for ps in models.PatchSet.gql('WHERE ANCESTOR IS :1', change):
    for rev in models.RevisionId.get_for_patchset(ps):
      rev.patchset = None
      rev.put()

  tbd = []
  for cls in [models.Patch,
              models.PatchSet,
              models.PatchSetFilenames,
              models.Comment,
              models.Message,
              models.ReviewStatus]:
    tbd += cls.gql('WHERE ANCESTOR IS :1', change).fetch(50)
    if len(tbd) > 100:
      db.delete(tbd)
      return respond(request, 'delete_loop.html',
                     {'change': change,
                      'del_url': '/%s/delete' % id})

  tbd += [change]
  db.delete(tbd)
  return HttpResponseRedirect('/mine')


@xsrf_required
@patchset_required
def merge(request):
  """/<change>/merge/<patchset> - Submit a change for merge."""
  change = request.change
  patchset = request.patchset
  patchset.patches = list(patchset.patch_set.order('filename'))

  reviewer_status = models.Change.get_reviewer_status(
      change.get_review_status())
  ready_to_submit = project.ready_to_submit(
      change.dest_branch,
      change.owner,
      reviewer_status,
      patchset.filenames,
      request.user)

  if not ready_to_submit['can_submit']:
    # Again, the button shouldn't have been there in this case.
    # Just send 'em back to the change page.
    return HttpResponseRedirect('/%d' % change.key().id())

  try:
    change.submit_merge(patchset)
    change.put()
  except models.InvalidSubmitMergeException, why:
    return HttpResponseForbidden(str(why))
  return HttpResponseRedirect('/mine')


@patch_required
def patch(request):
  """/<change>/patch/<patchset>/<patch> - View a raw patch."""
  return patch_helper(request)


def patch_helper(request, nav_type='patch'):
  """Returns a unified diff.

  Args:
    request: Django Request object.
    nav_type: the navigation used in the url (i.e. patch/diff/diff2).  Normally
      the user looks at either unified or side-by-side diffs at one time, going
      through all the files in the same mode.  However, if side-by-side is not
      available for some files, we temporarly switch them to unified view, then
      switch them back when we can.  This way they don't miss any files.

  Returns:
    Whatever respond() returns.
  """
  _add_next_prev(request.patchset, request.patch)
  request.patch.nav_type = nav_type
  parsed_lines = patching.ParsePatchToLines(request.patch.patch_lines)
  if parsed_lines is None:
    return HttpResponseNotFound('Can\'t parse the patch')
  rows = engine.RenderUnifiedTableRows(request, parsed_lines)
  return respond(request, 'patch.html',
                 {'patch': request.patch,
                  'patchset': request.patchset,
                  'rows': rows,
                  'change': request.change})


def _get_context_for_user(request):
  """Returns the context setting for a user.

  The value is validated against models.CONTEXT_CHOICES.
  If an invalid value is found, the value is overwritten with
  models.DEFAULT_CONTEXT.
  """
  if request.user:
    account = models.Account.current_user_account
    default_context = account.default_context
  else:
    default_context = models.DEFAULT_CONTEXT
  try:
    context = int(request.GET.get("context", default_context))
  except ValueError:
    context = default_context
  if context not in models.CONTEXT_CHOICES:
    context = models.DEFAULT_CONTEXT
  return context


@patch_required
def diff(request):
  """/<change>/diff/<patchset>/<patch> - View a patch as a side-by-side diff"""
  patchset = request.patchset
  patch = request.patch

  context = _get_context_for_user(request)
  rows = _get_diff_table_rows(request, patch, context)
  if isinstance(rows, HttpResponse):
    return rows

  _add_next_prev(patchset, patch)
  return respond(request, 'diff.html',
                 {'change': request.change, 'patchset': patchset,
                  'patch': patch, 'rows': rows,
                  'context': context, 'context_values': models.CONTEXT_CHOICES})


def _get_diff_table_rows(request, patch, context):
  """Helper function that returns rendered rows for a patch"""
  chunks = patching.ParsePatchToChunks(patch.patch_lines,
                                       patch.filename)
  if chunks is None:
    # If the patch has nothing in it, try using the
    # unified diff patch view instead as then we can
    # at least see the patch in the UI.
    #
    return HttpResponseRedirect('/%d/patch/%d/%s' % (
             patch.patchset.change.key().id(),
             patch.patchset.key().id(),
             patch.id))

  return list(engine.RenderDiffTableRows(request, patch.old_lines,
                                         chunks, patch,
                                         context=context))


@patch_required
def diff_skipped_lines(request, id_before, id_after, where):
  """/<change>/diff/<patchset>/<patch> - Returns a fragment of skipped lines"""
  patchset = request.patchset
  patch = request.patch

  # TODO: allow context = None?
  rows = _get_diff_table_rows(request, patch, 10000)
  if isinstance(rows, HttpResponse):
    return rows
  return _get_skipped_lines_response(rows, id_before, id_after, where)


def _get_skipped_lines_response(rows, id_before, id_after, where):
  """Helper function that creates a Response object for skipped lines"""
  response_rows = []
  id_before = int(id_before)
  id_after = int(id_after)

  if where == "b":
    rows.reverse()

  for row in rows:
    m = re.match('^<tr( name="hook")? id="pair-(?P<rowcount>\d+)">', row)
    if m:
      curr_id = int(m.groupdict().get("rowcount"))
      if curr_id < id_before or curr_id > id_after:
        continue
      if where  == "b" and curr_id <= id_after:
        response_rows.append(row)
      elif where == "t" and curr_id >= id_before:
        response_rows.append(row)
      if len(response_rows) >= 10:
        break

  # Create a usable structure for the JS part
  response = []
  dom = ElementTree.parse(StringIO('<div>%s</div>' % "".join(response_rows)))
  for node in dom.getroot().getchildren():
    content = "\n".join([ElementTree.tostring(x) for x in node.getchildren()])
    response.append([node.items(), content])
  return HttpResponse(simplejson.dumps(response))


def _get_diff2_data(request, ps_left_id, ps_right_id, patch_id, context):
  """Helper function that returns objects for diff2 views"""
  ps_left = models.PatchSet.get_by_id(int(ps_left_id), parent=request.change)
  if ps_left is None:
    return HttpResponseNotFound('No patch set exists with that id (%s)' %
                                ps_left_id)
  ps_left.change = request.change
  ps_right = models.PatchSet.get_by_id(int(ps_right_id), parent=request.change)
  if ps_right is None:
    return HttpResponseNotFound('No patch set exists with that id (%s)' %
                                ps_right_id)
  ps_right.change = request.change
  patch_right = models.Patch.get_patch(ps_right, patch_id)
  if patch_right is None:
    return HttpResponseNotFound('No patch exists with that id (%s/%s)' %
                                (ps_right_id, patch_id))
  patch_right.patchset = ps_right
  # Now find the corresponding patch in ps_left
  patch_left = models.Patch.gql('WHERE patchset = :1 AND filename = :2',
                                ps_left, patch_right.filename).get()
  if patch_left is None:
    return HttpResponseNotFound(
        "Patch set %s doesn't have a patch with filename %s" %
        (ps_left_id, patch_right.filename))

  rows = engine.RenderDiff2TableRows(request,
                                     patch_left.new_lines, patch_left,
                                     patch_right.new_lines, patch_right,
                                     context=context)
  rows = list(rows)
  if rows and rows[-1] is None:
    del rows[-1]

  return dict(patch_left=patch_left, ps_left=ps_left,
              patch_right=patch_right, ps_right=ps_right,
              rows=rows)


@change_required
def diff2(request, ps_left_id, ps_right_id, patch_id):
  """/<change>/diff2/... - View the delta between two different patch sets."""
  context = _get_context_for_user(request)
  data = _get_diff2_data(request, ps_left_id, ps_right_id, patch_id, context)
  if isinstance(data, HttpResponseNotFound):
    return data

  _add_next_prev(data["ps_right"], data["patch_right"])
  return respond(request, 'diff2.html',
                 {'change': request.change,
                  'ps_left': data["ps_left"],
                  'patch_left': data["patch_left"],
                  'ps_right': data["ps_right"],
                  'patch_right': data["patch_right"],
                  'rows': data["rows"],
                  'patch_id': patch_id,
                  'context': context,
                  'context_values': models.CONTEXT_CHOICES,
                  })


@change_required
def diff2_skipped_lines(request, ps_left_id, ps_right_id, patch_id,
                        id_before, id_after, where):
  """/<change>/diff2/... - Returns a fragment of skipped lines"""
  data = _get_diff2_data(request, ps_left_id, ps_right_id, patch_id, 10000)
  if isinstance(data, HttpResponseNotFound):
    return data
  return _get_skipped_lines_response(data["rows"], id_before, id_after, where)


def _add_next_prev(patchset, patch):
  """Helper to add .next and .prev attributes to a patch object."""
  patch.prev = patch.next = None
  patches = list(models.Patch.gql("WHERE patchset = :1 ORDER BY filename",
                                  patchset))
  patchset.patches = patches  # Required to render the jump to select.
  last = None
  for p in patches:
    if last is not None:
      if p.filename == patch.filename:
        patch.prev = last
      elif last.filename == patch.filename:
        patch.next = p
        break
    last = p


def inline_draft(request):
  """/inline_draft - Ajax handler to submit an in-line draft comment.

  This wraps _inline_draft(); all exceptions are logged and cause an
  abbreviated response indicating something went wrong.
  """
  if request.method != 'POST':
    return HttpResponse("POST request required.", status=405)
  try:
    return _inline_draft(request)
  except Exception, err:
    s = ''
    for k,v in request.POST.iteritems():
      s += '\n%s=%s' % (k, v)
    logging.exception('Exception in inline_draft processing:%s' % s)
    return HttpResponse('<font color="red">'
                        'Please report error "%s".'
                        '</font>'
                        % err.__class__.__name__)


def _inline_draft(request):
  """Helper to submit an in-line draft comment.
  """
  # Don't use @login_required; JS doesn't understand redirects.
  if not request.user:
    return HttpResponse('<font color="red">Not logged in</font>')

  if not is_xsrf_ok(request):
    return HttpResponse('<font color="red">'
                        'Stale xsrf signature.<br />'
                        'Please reload the page and try again.'
                        '</font>')

  snapshot = request.POST.get('snapshot')
  assert snapshot in ('old', 'new'), repr(snapshot)
  left = (snapshot == 'old')
  side = request.POST.get('side')
  assert side in ('a', 'b'), repr(side)  # Display left (a) or right (b)
  change_id = int(request.POST['change'])
  change = models.Change.get_by_id(change_id)
  assert change  # XXX
  patchset_id = request.POST.get('patchset') or request.POST[side == 'a' and 'ps_left' or 'ps_right']
  patchset = models.PatchSet.get_by_id(int(patchset_id), parent=change)
  assert patchset  # XXX
  patch_id = request.POST.get('patch') or request.POST[side == 'a' and 'patch_left' or 'patch_right']
  patch = models.Patch.get_patch(patchset, patch_id)
  assert patch  # XXX
  text = request.POST.get('text')
  lineno = int(request.POST['lineno'])
  message_id = request.POST.get('message_id')
  comment = None
  if message_id:
    comment = models.Comment.get_by_key_name(message_id, parent=patch)
    if comment is None or not comment.draft or comment.author != request.user:
      comment = None
      message_id = None
  if not message_id:
    # Prefix with 'z' to avoid key names starting with digits.
    message_id = 'z' + binascii.hexlify(_random_bytes(16))

  if not text.rstrip():
    if comment is not None:
      assert comment.draft and comment.author == request.user
      comment.delete()  # Deletion
      comment = None
      # Re-query the comment count.
      models.Account.current_user_account.update_drafts(change)
  else:
    if comment is None:
      comment = models.Comment(
                  patch=patch,
                  key_name=message_id,
                  parent=patch)
    comment.lineno = lineno
    comment.left = left
    comment.author = request.user
    comment.text = db.Text(text)
    comment.message_id = message_id
    comment.put()
    # The actual count doesn't matter, just that there's at least one.
    models.Account.current_user_account.update_drafts(change, 1)

  query = models.Comment.gql(
      'WHERE patch = :patch AND lineno = :lineno AND left = :left '
      'ORDER BY date',
      patch=patch, lineno=lineno, left=left)
  comments = list(c for c in query if not c.draft or c.author == request.user)
  if comment is not None and comment.author is None:
    # Show anonymous draft even though we don't save it
    comments.append(comment)
  if not comments:
    return HttpResponse(' ')
  for c in comments:
    c.complete(patch)
  return render_to_response('inline_comment.html',
                            {'inline_draft_url': '/inline_draft',
                             'user': request.user,
                             'patch': patch,
                             'patchset': patchset,
                             'change': change,
                             'comments': comments,
                             'lineno': lineno,
                             'snapshot': snapshot,
                             'side': side})


class PublishCommentsForm(BaseForm):
  _template = 'publish.html'

  reviewers = forms.CharField(required=False,
                              max_length=1000,
                              widget=forms.TextInput(attrs={'size': 60}))
  cc = forms.CharField(required=False,
                       max_length=1000,
                       label = 'CC',
                       widget=forms.TextInput(attrs={'size': 60}))
  send_mail = forms.BooleanField(required=False)
  message = forms.CharField(required=False,
                            max_length=10000,
                            widget=forms.Textarea(attrs={'cols': 60}))

  lgtm = forms.CharField(required=False, label='Code review')
  verified = forms.BooleanField(required=False,
                                label='Verified')
  abandoned = forms.BooleanField(required=False,
                                label='Abandoned')



  def __init__(self, *args, **kwargs):
    is_initial = kwargs.pop('is_initial', False)
    self.user_can_approve = kwargs.pop('user_can_approve', False)
    self.user_can_verify = kwargs.pop('user_can_verify', False)
    self.user_is_owner = kwargs.pop('user_is_owner', False)
    self.user_can_abandon = kwargs.pop('user_can_abandon', False)

    BaseForm.__init__(self, *args, **kwargs)

    if is_initial:
      # only show the available lgtm options
      lgtm_field = self.fields.get("lgtm")
      if lgtm_field:
        if self.user_can_approve:
          lgtm_field.widget = forms.RadioSelect(choices=models.LGTM_CHOICES)
        else:
          lgtm_field.widget = forms.RadioSelect(
                 choices=models.LIMITED_LGTM_CHOICES)
      # only show verified if the user can do it
      if not self.user_can_verify:
        del self.fields['verified']

      # disable the abandon button if the user can't change it
      if not self.user_can_abandon:
        del self.fields['abandoned']
      self.is_abandoned = kwargs['initial'].get('is_abandoned', False)

  @classmethod
  def _init(cls, state):
    request, change = state
    user = request.user

    reviewers = list(change.reviewers)
    cc = list(change.cc)

    if user != change.owner and user.email() not in reviewers:
      reviewers.append(user.email())
      if user.email() in cc:
        cc.remove(user.email())

    (user_can_approve,user_can_verify) = project.user_can_approve(
          request.user, change)

    # Pick the proper review / verify status
    review = change.get_review_status(user)
    if review:
      lgtm = _restrict_lgtm(review.lgtm, user_can_approve)
      verified = review.verified
    else:
      lgtm = 'abstain'
      verified = False

    user_can_abandon = change.user_can_edit(user) and not change.merged
    is_abandoned = change.closed and not change.merged

    return {'initial': {
              'reviewers': ', '.join(reviewers),
              'cc': ', '.join(cc),
              'send_mail': True,
              'lgtm': lgtm,
              'verified': verified,
              'abandoned': is_abandoned,
              },
            'user_can_approve': user_can_approve,
            'user_can_verify': user_can_verify,
            'user_is_owner': user == change.owner,
            'user_can_abandon': user_can_abandon,
            'is_initial': True,
          }

  def _save(self, cd, state):
    request, change = state
    user = request.user

    tbd, comments = _get_draft_comments(request, change)
    reviewers = _get_emails(self, 'reviewers')
    cc = _get_emails(self, 'cc')
    (self.user_can_approve,self.user_can_verify) = project.user_can_approve(
          request.user, change)
    lgtm = _restrict_lgtm(cd.get('lgtm', 'abstain'), self.user_can_approve)
    verified = _restrict_verified(cd.get('verified', False),
          self.user_can_verify)

    logging.info("user_can_edit=" + str(change.user_can_edit(user))
        + " merged=" + str(change.merged) + " field=" + str(cd['abandoned']))

    if change.user_can_edit(user):
      if not change.merged:
        change.closed = cd['abandoned']

    if user != change.owner:
      # Owners shouldn't have their own review status as it
      # is implied that 'lgtm' and verified by them.
      review_status = _update_review_status(change,
                                            user,
                                            lgtm,
                                            verified)
      tbd.append(review_status)

    change.set_reviewers(reviewers)
    change.cc = cc
    change.update_comment_count(len(comments))

    msg = _make_comment_message(request, change, lgtm, verified,
                        cd['message'],
                        comments,
                        cd['send_mail'])
    if msg:
      tbd.append(msg)
    tbd.append(change)

    while tbd:
      db.put(tbd[:50])
      tbd = tbd[50:]
    models.Account.current_user_account.update_drafts(change, 0)

@change_required
@login_required
def publish(request):
  """ /<change>/publish - Publish draft comments and send mail."""
  change = request.change

  tbd, comments = _get_draft_comments(request, change, True)
  preview = _get_draft_details(request, comments)

  def done():
    return HttpResponseRedirect('/%s' % change.key().id())
  return process_form(request, PublishCommentsForm, (request, change), done,
                      {'change': change,
                       'preview' : preview})


def _update_review_status(change, user, lgtm, verified):
  """Creates / updates the ReviewStatus for a user, returns that object."""
  review = change.set_review_status(user)
  review.lgtm = lgtm
  review.verified = verified
  return review



def _encode_safely(s):
  """Helper to turn a unicode string into 8-bit bytes."""
  if isinstance(s, unicode):
    s = s.encode('utf-8')
  return s


def _get_draft_comments(request, change, preview=False):
  """Helper to return objects to put() and a list of draft comments.

  If preview is True, the list of objects to put() is empty to avoid changes
  to the datastore.

  Args:
    request: Django Request object.
    change: Change instance.
    preview: Preview flag (default: False).

  Returns:
    2-tuple (put_objects, comments).
  """
  comments = []
  tbd = []
  dps = dict()

  # XXX Should request all drafts for this change once, now we can.
  for patchset in change.patchset_set.order('id'):
    ps_comments = list(models.Comment.gql(
        'WHERE ANCESTOR IS :1 AND author = :2 AND draft = TRUE',
        patchset, request.user))
    if ps_comments:
      patches = dict((p.key(), p) for p in patchset.patch_set)
      for p in patches.itervalues():
        p.patchset = patchset
      for c in ps_comments:
        c.draft = False
        # XXX Using internal knowledge about db package: the key for
        # reference property foo is stored as _foo.
        pkey = getattr(c, '_patch', None)
        if pkey in patches:
          patch = patches[pkey]
          c.patch = patch
        if pkey not in dps:
          dps[pkey] = c.patch
        c.patch.update_comment_count(1)
      if not preview:
        tbd += ps_comments
      ps_comments.sort(key=lambda c: (c.patch.filename, not c.left,
                                      c.lineno, c.date))
      comments += ps_comments
  if not preview:
    tbd += dps.values()
  return tbd, comments

FILE_LINE = '======================================================================'
COMMENT_LINE = '------------------------------'

def _get_draft_details(request, comments):
  """Helper to display comments with context in the email message."""
  last_key = None
  output = []
  linecache = {}  # Maps (c.patch.filename, c.left) to list of lines
  for c in comments:
    if (c.patch.filename, c.left) != last_key:
      if not last_key is None:
        output.append('%s\n' % FILE_LINE)
      url = request.build_absolute_uri('/%d/diff/%d/%s' %
                                       (request.change.key().id(),
                                        c.patch.patchset.key().id(),
                                        c.patch.id))
      output.append('\n%s\n%s\nFile %s:' % (FILE_LINE, url, c.patch.filename))
      last_key = (c.patch.filename, c.left)
      patch = c.patch
      if c.left:
        linecache[last_key] = patch.old_lines
      else:
        linecache[last_key] = patch.new_lines
    file_lines = linecache.get(last_key, ())
    context = ''
    if 1 <= c.lineno <= len(file_lines):
      context = file_lines[c.lineno - 1].strip()
    url = request.build_absolute_uri('/%d/diff/%d/%s#%scode%d' %
                                     (request.change.key().id(),
                                      c.patch.patchset.key().id(),
                                      c.patch.id,
                                      c.left and "old" or "new",
                                      c.lineno))
    output.append('%s\nLine %d: %s\n%s'  % (COMMENT_LINE, c.lineno,
                                                context, c.text.rstrip()))
  if not last_key is None:
    output.append('%s\n' % FILE_LINE)
  return '\n'.join(output)

def _make_comment_message(request, change, lgtm, verified, message,
                  comments=None, send_mail=False):
  """Helper to create a Message instance and optionally send an email."""

  prefix = ''
  if len(lgtm):
    prefix = prefix + [y for (x,y) in models.LGTM_CHOICES
                       if x == lgtm][0] + '\n'
  if verified:
    prefix = prefix + 'Verified.\n'
  if prefix:
    prefix = prefix + '\n'

  message = message.replace('\r\n', '\n')
  message = prefix + message

  if comments:
    details = _get_draft_details(request, comments)
  else:
    details = ''

  template_args = {
      'message': message,
      'details': details,
    }

  return email.send_change_message(
                              request, change,
                              'mails/comment.txt', template_args, request.user,
                              send_mail, 'mails/comment-email.txt')


@xsrf_required
@posted_change_required
def star(request):
  account = models.Account.current_user_account
  if account.stars is None:
    account.stars = []
  id = request.change.key().id()
  if id not in account.stars:
    account.stars.append(id)
    account.put()
  return respond(request, 'change_star.html', {'change': request.change})


@xsrf_required
@posted_change_required
def unstar(request):
  account = models.Account.current_user_account
  if account.stars is None:
    account.stars = []
  id = request.change.key().id()
  if id in account.stars:
    account.stars[:] = [i for i in account.stars if i != id]
    account.put()
  return respond(request, 'change_star.html', {'change': request.change})


@gae_admin_required
def download_bundle(request, bundle_id, segment_id):
  """/download/bundle(\d+)_(\d+) - get a bundle segment"""
  rb = git_models.ReceivedBundle.get_by_id(int(bundle_id))
  if not rb:
    return HttpResponseNotFound('No bundle exists with that id (%s)' % bundle_id)
  seg = rb.get_segment(int(segment_id))
  if not seg:
    return HttpResponseNotFound('No segment %s in bundle %s' % (segment_id,bundle_id))
  return HttpResponse(seg.bundle_data,
                      content_type='application/x-git-bundle-segment')


### Administration ###

@project_owner_or_admin_required
def admin(request):
  """/admin - user & other settings"""
  return respond(request, 'admin.html', {})

@gae_admin_required
def admin_settings(request):
  settings = models.Settings.get_settings()
  return respond(request, 'admin_settings.html', {
            'settings': settings,
            'CUR_SCHEMA_VERSION': models.CUR_SCHEMA_VERSION,
            'from_email_test_xsrf': xsrf_for('/admin/settings/from_email_test')
          })

class AdminSettingsAnalyticsForm(BaseForm):
  _template = 'admin_settings_analytics.html'

  analytics = forms.CharField(required=False, max_length=20,
                              widget=forms.TextInput(attrs={'size': 20}))

  @classmethod
  def _init(cls, state):
    settings = models.Settings.get_settings()
    return {'initial': {
                'analytics': settings.analytics,
              }
            }

  def _save(self, cd, change):
    settings = models.Settings.get_settings()
    settings.analytics = cd['analytics']
    settings.put()

@gae_admin_required
def admin_settings_analytics(request):
  def done():
    return HttpResponseRedirect('/admin/settings')
  return process_form(request, AdminSettingsAnalyticsForm, None, done)

class AdminSettingsFromEmailForm(BaseForm):
  _template = 'admin_settings_from_email.html'

  from_email = forms.CharField(required=False,
                              max_length=60,
                              widget=forms.TextInput(attrs={'size': 60}))

  @classmethod
  def _init(cls, state):
    settings = models.Settings.get_settings()
    return {'initial': {
                'from_email': settings.from_email,
              }
            }

  def _save(self, cd, change):
    settings = models.Settings.get_settings()
    settings.from_email = cd['from_email']
    settings.put()

@gae_admin_required
def admin_settings_from_email(request):
  def done():
    return HttpResponseRedirect('/admin/settings')
  return process_form(request, AdminSettingsFromEmailForm, None, done)

class AdminSettingsMergeLogEmailForm(BaseForm):
  _template = 'admin_settings_merge_log_email.html'

  merge_log_email = forms.CharField(required=False,
                                    max_length=60,
                                    widget=forms.TextInput(attrs={'size': 60}))

  @classmethod
  def _init(cls, state):
    settings = models.Settings.get_settings()
    return {'initial': {
                'merge_log_email': settings.merge_log_email,
              }
            }

  def _save(self, cd, change):
    settings = models.Settings.get_settings()
    settings.merge_log_email = cd['merge_log_email']
    settings.put()

@gae_admin_required
def admin_settings_merge_log_email(request):
  def done():
    return HttpResponseRedirect('/admin/settings')
  return process_form(request,
                      AdminSettingsMergeLogEmailForm,
                      None,
                      done)

class AdminSettingsCanonicalUrlForm(BaseForm):
  _template = 'admin_settings_canonical_url.html'

  url = forms.CharField(required=True)

  @classmethod
  def _init(cls, state):
    settings = models.Settings.get_settings()
    return {'initial': {
                'url': settings.canonical_url,
              }
            }

  def _save(self, cd, change):
    settings = models.Settings.get_settings()
    settings.canonical_url = cd['url']
    settings.put()

@gae_admin_required
def admin_settings_canonical_url(request):
  def done():
    return HttpResponseRedirect('/admin/settings')
  return process_form(request, AdminSettingsCanonicalUrlForm, None, done)


class AdminSettingsSourceBrowserUrlForm(BaseForm):
  _template = 'admin_settings_source_browser_url.html'

  url = forms.CharField(required=True,
                        widget=forms.TextInput(attrs={'size': 60}))

  @classmethod
  def _init(cls, state):
    settings = models.Settings.get_settings()
    return {'initial': {
                'url': settings.source_browser_url,
              }
            }

  def _save(self, cd, change):
    settings = models.Settings.get_settings()
    settings.source_browser_url = cd['url']
    settings.put()

@gae_admin_required
def admin_settings_source_browser_url(request):
  def done():
    return HttpResponseRedirect('/admin/settings')
  return process_form(request, AdminSettingsSourceBrowserUrlForm, None, done)


@gae_admin_required
def admin_settings_from_email_test(request):
  if request.method == 'POST':
    if is_xsrf_ok(request, xsrf=request.POST.get('xsrf')):
      address = email.get_default_sender()
      try:
        mail.check_email_valid(address, 'blah')
        email.send(None, [models.Account.current_user_account], 'test',
                              'mails/from_email_test.txt', None)
        status = 'email sent'
      except mail.InvalidEmailError:
        status = 'invalid email address'
      return respond(request, 'admin_settings_from_email_test.html', {
                  'status': status,
                  'address': address
                })
  return HttpResponse('<font color="red">Ich don\'t think so!</font>')



### User Profiles ###

def validate_real_name(real_name):
  """Returns None if real_name is fine and an error message otherwise."""
  if not real_name:
    return 'Name cannot be empty.'
  elif real_name == 'me':
    return 'Of course, you are what you are.  But \'me\' is for everyone.'
  else:
    return None


@user_key_required
def user_popup(request):
  """/user_popup - Pop up to show the user info."""
  try:
    user = request.user_to_show
    def gen():
      account = models.Account.get_account_for_user(user)
      return render_to_response('user_popup.html', {'account': account})
    return MemCacheKey('user_popup:' + user.email(), 300).get(gen)
  except Exception, err:
    logging.exception('Exception in user_popup processing:')
    return HttpResponse(
      '<font color="red">Error: %s; please report!</font>'
      % err.__class__.__name__)


class AdminDataStoreDeleteForm(BaseForm):
  _template = 'admin_datastore_delete.html'

  really = forms.CharField(required=True)

  def _save(self, cd, request):
    if cd['really'] != 'DELETE EVERYTHING':
      return self

    max_cnt = 50
    rm = []
    specials = []
    for cls in [models.ApprovalRight,
                models.Project,
                models.Branch,
                models.RevisionId,
                models.BuildAttempt,
                models.Change,
                models.PatchSetFilenames,
                models.PatchSet,
                models.Message,
                models.DeltaContent,
                models.Patch,
                models.Comment,
                models.Bucket,
                models.ReviewStatus,
                models.Account,
                models.AccountGroup,
                git_models.ReceivedBundleSegment,
                git_models.ReceivedBundle,
               ]:
      all = cls.all().fetch(max_cnt)
      if cls == models.Account:
        for a in all:
          if a.key() == request.account.key():
            specials.append(a)
          else:
            rm.append(a)
      elif cls == models.AccountGroup:
        for a in all:
          if a.is_auto_group:
            specials.append(a)
          else:
            rm.append(a)
      else:
        rm.extend(all)
      if len(rm) >= max_cnt:
        break

    if rm:
      # Delete this block of data and continue via
      # a JavaScript reload in the browser.
      #
      db.delete(rm)
      return self

    # We are done with the bulk of the content, but we need
    # to clean up a few special objects.
    #
    library._user_cache.clear()
    models.Settings._Key.clear()

    specials.extend(models.Settings.all().fetch(100))
    db.delete(specials)
    return None

@gae_admin_required
def admin_datastore_delete(request):
  def done():
    return HttpResponseRedirect('/')
  return process_form(request, AdminDataStoreDeleteForm, request, done)


class AdminDataStoreUpgradeForm(BaseForm):
  _template = 'admin_datastore_upgrade.html'

  really = forms.CharField(required=True)
  stage = forms.IntegerField(widget=forms.HiddenInput(), initial=1)

  def _save(self, cd, request):
    if cd['really'] != 'UPGRADE':
      return self

    stage = cd['stage']
    s = models.Settings.get_settings()
    name = '_upgrade%d_stage%d' % (s.schema_version, stage)
    try:
      f = getattr(self, name)
    except AttributeError:
      return http.HttpResponseServerError("no %s" % name)

    if not f():
      # This stage function needs to run again.
      #
      return self

    name = '_upgrade%d_stage%d' % (s.schema_version, stage + 1)
    if getattr(self, name, None):
      # There is another stage function in this version.
      #
      new_cd = dict(cd)
      new_cd['stage'] = stage + 1
      return self.__class__(initial=new_cd)

    # The current schema version is completely upgraded.
    #
    s.schema_version += 1
    s.put()

    name = '_upgrade%d_stage%d' % (s.schema_version, 1)
    if getattr(self, name, None):
      # There is another upgrade to execute.
      #
      new_cd = dict(cd)
      new_cd['stage'] = 1
      return self.__class__(initial=new_cd)

    # There are no further upgrades to process.
    #
    return None

  def _upgrade0_stage1(self):
    all = models.gql(models.ApprovalRight,
                     'WHERE last_backed_up = :1',
                     0).fetch(25)
    if not all:
      return True

    sb = models.AccountGroup.get_group_for_name('submitters')
    for ar in all:
      ar.last_backed_up = 1
      ar.submitters_groups.append(sb.key())
      ar.put()
    return False

  def _upgrade0_stage2(self):
    all = models.gql(models.ApprovalRight,
                     'WHERE last_backed_up = :1',
                     1).fetch(25)
    if not all:
      return True

    for ar in all:
      ar.last_backed_up = 0
      ar.put()
    return False

@gae_admin_required
def admin_datastore_upgrade(request):
  def done():
    return HttpResponseRedirect('/')
  return process_form(request, AdminDataStoreUpgradeForm, request, done)
