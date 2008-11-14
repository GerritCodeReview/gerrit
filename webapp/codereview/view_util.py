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

import base64
import hashlib
import hmac
import logging
import os
import time
import urllib

from google.appengine.api import users
from google.appengine.ext import db
from google.appengine.runtime import DeadlineExceededError

import django
from django.template import loader as template_loader
from django.http import HttpResponse, \
                        HttpResponseRedirect, \
                        HttpResponseForbidden, \
                        HttpResponseNotFound
from django.forms import ValidationError

# Add our own template library.
_library_name = __name__.rsplit('.', 1)[0] + '.library'
if not django.template.libraries.get(_library_name, None):
  django.template.add_to_builtins(_library_name)
del _library_name

import models

IS_DEV = os.environ['SERVER_SOFTWARE'].startswith('Dev')
MAX_XSRF_WINDOW = 4 * 60 * 60  # seconds

### Decorators for request handlers ###

def login_required(func):
  """Decorator that redirects to the login page if you're not logged in."""
  def login_wrapper(request, *args, **kwds):
    if request.user is None:
      return HttpResponseRedirect(
          users.create_login_url(request.get_full_path()))
    if not request.account.welcomed and request.path != '/settings/welcome':
      return HttpResponseRedirect(
          '/settings/welcome?dest=%s' % urllib.quote(request.get_full_path()))
    return func(request, *args, **kwds)
  return login_wrapper


def user_required(func):
  """Decorator that returns a 500 you're not logged in.
  
  Good for urls that are only meant for form submission, you should probably
  pair this with @xsrf_required."""
  def login_wrapper(request, *args, **kwds):
    if request.user is None:
      return http.HttpResponseServerError("error 500: multiple matches for hash")

      return HttpResponseRedirect(
          users.create_login_url(request.get_full_path()))
    if not request.account.welcomed and request.path != '/settings/welcome':
      return HttpResponseRedirect(
          '/settings/welcome?dest=%s' % urllib.quote(request.get_full_path()))
    return func(request, *args, **kwds)
  return login_wrapper


def gae_admin_required(func):
  def admin_wrapper(request, *args, **kwds):
    """Decorator that insists that you are a GAE admin/developer.
    """
    if request.user is None:
      path = request.get_full_path()
      return HttpResponseRedirect(users.create_login_url(path))
    if not request.is_gae_admin:
      return HttpResponseNotFound('Page not found')
    return func(request, *args, **kwds)
  return admin_wrapper


def admin_required(func):
  def admin_wrapper(request, *args, **kwds):
    """Decorator that insists that you're logged in as administratior."""
    if request.user is None:
      return HttpResponseRedirect(
          users.create_login_url(request.get_full_path()))
    if not request.user_is_admin:
      return HttpResponseNotFound('Page not found')
    return func(request, *args, **kwds)
  return admin_wrapper


def project_owner_or_admin_required(func):
  def admin_wrapper(request, *args, **kwds):
    """Decorator that insists that you're logged in as administratior."""
    if request.user is None:
      return HttpResponseRedirect(
          users.create_login_url(request.get_full_path()))
    if not (request.user_is_admin or request.projects_owned_by_user):
      return HttpResponseNotFound('Page not found')
    return func(request, *args, **kwds)
  return admin_wrapper


def devenv_required(func):
  def devenv_wrapper(request, *args, **kwds):
    """Decorator that insists that you're on the development server."""
    if not IS_DEV:
      return HttpResponseNotFound('Page not found')
    return func(request, *args, **kwds)
  return devenv_wrapper


def change_required(func):
  """Decorator that processes the change_id handler argument."""
  def change_wrapper(request, change_id, *args, **kwds):
    try:
      change = models.Change.get_by_id(int(change_id))
    except db.BadKeyError:
      return HttpResponseNotFound('No change exists with that id (%s)' %
                                  change_id)
    if change is None:
      return HttpResponseNotFound('No change exists with that id (%s)' %
                                  change_id)
    request.change = change
    return func(request, *args, **kwds)
  return change_wrapper


def posted_change_required(func):
  """Decorator that processes POST['change_id']
  """
  def change_wrapper(request, *args, **kwds):
    try:
      change_id = request.POST['change_id']
    except KeyError:
      return HttpResponseNotFound('No change supplied.')
    change = models.Change.get_by_id(int(change_id))
    if change is None:
      return HttpResponseNotFound('No change exists with that id (%s)' %
                                  change_id)
    request.change = change
    return func(request, *args, **kwds)
  return change_wrapper


def user_key_required(func):
  """Decorator that processes the user handler argument."""
  def user_key_wrapper(request, user_key, *args, **kwds):
    user_key = urllib.unquote(user_key)
    if '@' in user_key:
      request.user_to_show = users.User(user_key)
    elif ',,' in user_key:
      request.user_to_show = users.User(user_key.replace(',,','@'))
    else:
      accounts = models.Account.get_accounts_for_real_name(user_key)
      if not accounts:
        logging.info("account not found for real_name %s" % user_key)
        return HttpResponseNotFound('No user found with that key (%s)' %
                                    user_key)
      request.user_to_show = accounts[0].user
    return func(request, *args, **kwds)
  return user_key_wrapper


def change_owner_required(func):
  """Decorator that processes the change_id argument and insists you own it."""
  @change_required
  @login_required
  def change_owner_wrapper(request, *args, **kwds):
    if not request.change.user_can_edit():
      return HttpResponseForbidden('You do not own this change')
    return func(request, *args, **kwds)
  return change_owner_wrapper


def patchset_required(func):
  """Decorator that processes the patchset_id argument."""
  @change_required
  def patchset_wrapper(request, patchset_id, *args, **kwds):
    patchset = models.PatchSet.get_by_id(int(patchset_id), parent=request.change)
    if patchset is None:
      return HttpResponseNotFound('No patch set exists with that id (%s)' %
                                  patchset_id)
    patchset.change = request.change
    request.patchset = patchset
    return func(request, *args, **kwds)
  return patchset_wrapper


def patch_required(func):
  """Decorator that processes the patch_id argument."""
  @patchset_required
  def patch_wrapper(request, patch_id, *args, **kwds):
    patch = models.Patch.get_patch(request.patchset, patch_id)
    if patch is None:
      return HttpResponseNotFound('No patch exists with that id (%s %s)' %
                                  (request.patchset.key().id(), patch_id))
    patch.patchset = request.patchset
    request.patch = patch
    return func(request, *args, **kwds)
  return patch_wrapper


### Render Django template ###

def _parse_template(name):
  return template_loader.get_template(name)

def _lookup_template(name):
  try:
    t = _template_cache[name]
  except KeyError:
    t = _parse_template(name)
    _template_cache[name] = t
  return t
_template_cache = {}

if IS_DEV:
  _get_template = _parse_template
else:
  _get_template = _lookup_template

def respond(request, template, params=None, status=200):
  """Render a response, passing standard stuff to the response.

  Args:
    request: The request object.
    template: The template name; '.html' is appended automatically.
    params: A dict giving the template parameters; modified in-place.

  Returns:
    A Django HttpResponse
  """
  try:
    if params is None:
      params = {}

    params['request'] = request
    params['user'] = request.user
    params['is_gae_admin'] = request.is_gae_admin
    params['is_dev'] = IS_DEV
    params['analytics'] = models.Settings.get_settings().analytics
    my_path = request.get_full_path()

    if request.user is None:
      params['sign_in'] = users.create_login_url(my_path)
    else:
      params['sign_out'] = users.create_logout_url(my_path)
      params['star_url'] = '/star'
      params['unstar_url'] = '/unstar'
      params['inline_draft_url'] = '/inline_draft'

    if not template.endswith('.html'):
      template += '.html'
    type = 'text/html; charset=UTF-8'

    ctx = django.template.Context(params)
    body = _get_template(template).render(ctx)
    return HttpResponse(content = body,
                        status = status,
                        content_type= type)
  except DeadlineExceededError:
    logging.exception('DeadlineExceededError')
    return HttpResponse(status=500, content='DeadlineExceededError')
  except MemoryError:
    logging.exception('MemoryError')
    return HttpResponse(status=500, content='MemoryError')
  except AssertionError:
    logging.exception('AssertionError')
    return HttpResponse(status=500, content='AssertionError')


### Form Handling ###

_xsrf_key = None
_xsrf_now = None
_xsrf_cache = {}

def _now():
  global _xsrf_now

  if _xsrf_now is None:
    _xsrf_now = time.time()
  return _xsrf_now

def _xsrf_sign(path, when):
  global _xsrf_key

  if _xsrf_key is None:
    _xsrf_key = models.Settings.get_settings().xsrf_key
    _xsrf_key = base64.b64decode(_xsrf_key)

  user = users.get_current_user()
  if user:
    user_name = user.email()
  else:
    user_name = '-'

  tok = [user_name, path, str(when)]
  xsrf = hmac.new(_xsrf_key, digestmod=hashlib.sha1)
  xsrf.update(':'.join(tok))
  return base64.b64encode(str(when) + ':' + xsrf.digest())

def _xsrf_check(path, xsrf):
  if not xsrf:
    return False
  try:
    when = int(base64.b64decode(xsrf).split(':', 2)[0])
  except TypeError:
    return False
  except ValueError:
    return False
  except UnicodeEncodeError:
    return False
  if abs(_now() - when) > MAX_XSRF_WINDOW:
    return False
  return xsrf == _xsrf_sign(path, when)


class BaseForm(django.forms.Form):
  """A Django form which provides automatic XSRF protection,
     assuming it is parsed/displayed by process_form()
  """

  # Name of the template the form renders itself as.
  #
  # Must be replaced by the subclass.
  #
  _template = None

  xsrf = django.forms.CharField(required = False,
                                widget = django.forms.HiddenInput)

  @classmethod
  def _init(cls, state):
    """Obtain the initial values for the fields of the form.

       Args:
         state:  state object supplied by the caller of process_form
       Returns:
         dict of form constructor keywords; at minimum the key
         'initial' must be a dict
    """
    return {'initial': {}}

  def _post_init(self, state):
    """Called on the form, after it has been constructed, both in the
       initial request, and in the post-back.

       Args:
         state:  state object supplied by the caller of process_form
       Returns:
         nothing
    """
    pass

  def _pre_verify(self, get, post):
    """Gives the form a crack at the raw post data before verification.

       If this method returns an HttpResponse object, that will be
       returned, and _save will not be called.
    """
    return None

  def _save(self, cd, state):
    """Processes the form data, typically saving it to the db.

       Adding an error to self.errors['fieldname'] will cause
       the form to be redisplayed, in case the save routine finds
       problems with the submitted data and wants the user to try
       and correct them.

       Args:
         cd:  dict of cleaned field values (same as self.cleaned_data)
         state:  state object supplied by the caller of process_form
    """
    raise NotImplementedError()


def process_form(request, form_cls, state, done, params=None):
  """Display (or parse and process) an HTML form.

     Args:
       request:   Django request object
       form_cls:  class object for a subclass of BaseForm

       state:     any application state object, to be passed into
                  the _init and _save methods of BaseForm

       done:      a callable invoked after _save is successful;
                  its return value is the result of this function

       params:    dictional of additional parameters to pass into
                  the Django template
     Returns:
       an HttpResponse (or the result of done() if _save was called)
  """
  if params is None:
    params = {}

  def _handle_result(result):
    if isinstance(result, HttpResponse):
      return result
    elif isinstance(result, BaseForm):
      params['form'] = result
      return respond(request, result._template, params)
    
  if request.method == 'POST':
    form = form_cls(request.POST)
    form._post_init(state)

    if _xsrf_check(request.get_full_path(), request.POST.get('xsrf')):
      result = form._pre_verify(request.GET, request.POST)
      if result:
        return _handle_result(result)
      if form.is_valid():
        # for form.cleaned_data to exist
        result = form._save(form.cleaned_data, state)
        if result:
          return _handle_result(result)
        if form.is_valid():
          return done()
    else:
      if form.is_valid():  # this check forces cleaned_data
        i = dict(form.cleaned_data)
        i['xsrf'] = xsrf_for(request.get_full_path())
        form = form_cls(initial=i)
        form._errors = {}
        form._errors['xsrf'] = ['Form token timed out.  Try again.']
  else:
    kwargs = form_cls._init(state)
    kwargs['initial']['xsrf'] = xsrf_for(request.get_full_path())
    form = form_cls(**kwargs)
    form._post_init(state)

  params['form'] = form
  return respond(request, form._template, params)


def xsrf_for(path):
  try:
    return _xsrf_cache[path]
  except KeyError:
    pass
  r = _xsrf_sign(path, int(_now()))
  _xsrf_cache[path] = r
  return r

def is_xsrf_ok(request, path=None, xsrf=None):
  if path is None:
    path = request.get_full_path()
  if xsrf is None:
    xsrf = request.POST.get('xsrf')
  return _xsrf_check(path, xsrf)

def xsrf_required(func):
  """Decorator that requires invocation by HTTP POST only,
     and the form must have an 'xsrf' field with a valid
     xsrf key (see xsrf_for() to make such keys).

     Implies @login_required
  """
  def post_wrapper(request, *args, **kwds):
    if request.method != 'POST':
      return HttpResponse("POST request required.", status=405)
    if not is_xsrf_ok(request):
      return HttpResponse("Invalid xsrf signature."
                          " Reload the prior page.", status=405)
    return func(request, *args, **kwds)
  return login_required(post_wrapper)
