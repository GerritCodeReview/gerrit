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

"""App Engine data model (schema) definition for Gerrit."""

# Python imports
import base64
import datetime
import hashlib
import logging
import random
import re
import zlib

# AppEngine imports
from google.appengine.ext import db
from google.appengine.api import memcache
from google.appengine.api import users

# Local imports
from memcache import Key as MemCacheKey
import patching


DEFAULT_CONTEXT = 10
CONTEXT_CHOICES = (3, 10, 25, 50, 75, 100)
FETCH_MAX = 1000
MAX_DELTA_DEPTH = 10

LGTM_CHOICES = (
    ('lgtm', 'Looks good to me, approved.'),
    ('yes', 'Looks good to me, but someone else must approve.'),
    ('abstain', 'No score.'),
    ('no', 'I would prefer that you didn\'t submit this.'),
    ('reject', 'Do not submit.'),
  )
LIMITED_LGTM_CHOICES = [choice for choice in LGTM_CHOICES
        if choice[0] != 'lgtm' and choice[0] != 'reject']

### GQL query cache ###


_query_cache = {}

class BackedUpModel(db.Model):
  """Base class for our models that keeps a property used for backup."""

  last_backed_up = db.IntegerProperty(default=0)

  def __init__(self, *args, **kwargs):
    db.Model.__init__(self, *args, **kwargs)

def gql(cls, clause, *args, **kwds):
  """Return a query object, from the cache if possible.

  Args:
    cls: a BackedUpModel subclass.
    clause: a query clause, e.g. 'WHERE draft = TRUE'.
    *args, **kwds: positional and keyword arguments to be bound to the query.

  Returns:
    A db.GqlQuery instance corresponding to the query with *args and
    **kwds bound to the query.
  """
  query_string = 'SELECT * FROM %s %s' % (cls.kind(), clause)
  query = _query_cache.get(query_string)
  if query is None:
    _query_cache[query_string] = query = db.GqlQuery(query_string)
  query.bind(*args, **kwds)
  return query


### Exceptions ###

class InvalidLgtmException(Exception):
  """User is not alloewd to LGTM this change."""

class InvalidVerifierException(Exception):
  """User is not alloewd to verify this change."""

class InvalidSubmitMergeException(Exception):
  """The change cannot me scheduled for merge."""

class DeltaPatchingException(Exception):
  """Applying a patch yield the wrong hash."""


### Settings ###

def _genkey(n=26):
  k = ''.join(map(chr, (random.randrange(256) for i in xrange(n))))
  return base64.b64encode(k)

class Settings(BackedUpModel):
  """Global settings for the application instance."""

  analytics = db.StringProperty()
  internal_api_key = db.StringProperty()
  xsrf_key = db.StringProperty()
  from_email = db.StringProperty()
  canonical_url = db.StringProperty(default='')
  source_browser_url = db.StringProperty(default='')
  merge_log_email = db.StringProperty()

  _Key = MemCacheKey('Settings_Singleton')
  _LocalCache = None

  @classmethod
  def get_settings(cls):
    """Get the Settings singleton.

    If possible, get it from memcache.  If it's not there, it tries to do a 
    normal get().  Only if that fails does it call get_or_insert, because of
    possible contention errors due to get_or_insert's transaction.
    """
    if Settings._LocalCache is None:
      def read():
        result = cls.get_by_key_name('settings')
        if result:
          return result
        else:
          return cls.get_or_insert('settings',
                                    internal_api_key=_genkey(26),
                                    xsrf_key=_genkey(26))
      Settings._LocalCache = Settings._Key.get(read)
    return Settings._LocalCache

  def put(self):
    BackedUpModel.put(self)
    self._Key.clear()


### Approval rights ###

def _flatten_users_and_groups(users, groups):
  """Returns a set of the users and the groups provided"""
  result = set()
  for user in users:
    result.add(user)
  if groups:
    for group in db.get(groups):
      for user in group.members:
        result.add(user)
  return result


class ApprovalRight(BackedUpModel):
  """The tuple of a set of path patterns and a set of users who can approve
  changes for those paths."""
  files = db.StringListProperty()
  approvers_users = db.ListProperty(users.User)
  approvers_groups = db.ListProperty(db.Key)
  verifiers_users = db.ListProperty(users.User)
  verifiers_groups = db.ListProperty(db.Key)
  submitters_users = db.ListProperty(users.User)
  submitters_groups = db.ListProperty(db.Key)
  required = db.BooleanProperty()

  def approvers(self):
    """Returns a set of the users who are approvers."""
    return _flatten_users_and_groups(self.approvers_users,
        self.approvers_groups)

  def verifiers(self):
    """Returns a set of the users who are verifiers."""
    return _flatten_users_and_groups(self.verifiers_users,
        self.verifiers_groups)

  def submitters(self):
    """Returns a set of the users who are submitters."""
    return _flatten_users_and_groups(self.submitters_users,
        self.submitters_groups)

  @classmethod
  def validate_file(cls, file):
    """Returns whether this is a valid file path.
    
    The rules:
      - The length must be > 0
      - The file path must start with a '/'
      - The file path must contain either 0 or 1 '...'
      - If it contains one '...', it must either be last or directly
        after the first '/'
        
        These last two limitations could be removed someday but are
        good enough for now.
    """
    if len(file) == 0:
      return False
    if file[0] != '/':
      return False
    (before, during, after) = file.partition("...")
    if during == "" and after == "":
      return True
    if before != "/":
      return False
    if after.find("...") != -1:
      return False
    return True

### Projects ###

class Project(BackedUpModel):
  """An open source project.

  Projects have owners who can set approvers and stuff.
  """

  name = db.StringProperty(required=True)
  comment = db.StringProperty(required=False)
  owners_users = db.ListProperty(users.User)
  owners_groups = db.ListProperty(db.Key)
  code_reviews = db.ListProperty(db.Key)

  @classmethod
  def get_all_projects(cls):
    """Return all projects"""
    all = cls.all()
    all.order('name')
    return list(all)

  @classmethod
  def get_project_for_name(cls, name):
    return cls.gql('WHERE name=:name', name=name).get()

  def remove(self):
    """delete this project"""
    db.delete(ApprovalRight.get(self.code_reviews))
    self.delete()

  def set_code_reviews(self, approval_right_keys):
    for key in self.code_reviews:
      val = ApprovalRight.get(key)
      if val:
        db.delete(val)
    self.code_reviews = approval_right_keys

  def get_code_reviews(self):
    return [ApprovalRight.get(key) for key in self.code_reviews]

  def is_code_reviewed(self):
    return True

  def is_user_lead(self, user):
    if user in self.owners_users:
      return True
    for group_key in self.owners_groups:
      group = AccountGroup.get(group_key)
      if user in group.members:
        return True
    return False

  def put(self):
    memcache.flush_all()
    BackedUpModel.put(self)

  @classmethod
  def projects_owned_by_user(cls, user):
    memcache.flush_all()
    key = "projects_owned_by_user_%s" % user.email()
    result = memcache.get(key)
    if result is None:
      result = set(
          Project.gql("WHERE owner_users=:user", user=user).fetch(1000))
      groups = AccountGroup.gql("WHERE members=:user", user=user).fetch(1000)
      if groups:
        pr = Project.gql(
            "WHERE owners_groups IN :groups",
            groups=[g.key() for g in groups]).fetch(1000)
        result.update(pr)
      memcache.set(key, [p.key() for p in result])
    return result


class Branch(BackedUpModel):
  """A branch in a specific Project."""

  project = db.ReferenceProperty(Project, required=True)
  name = db.StringProperty(required=True) # == key

  status = db.StringProperty(choices=('NEEDS_MERGE',
                                      'MERGING',
                                      'BUILDING'))
  merge_submitted = db.DateTimeProperty()
  to_merge = db.ListProperty(db.Key) # PatchSets
  merging = db.ListProperty(db.Key) # PatchSets
  waiting = db.ListProperty(db.Key) # PatchSets

  @classmethod
  def get_or_insert_branch(cls, project, name):
    key = 'p.%s %s' % (project.key().id(), name)
    return cls.get_or_insert(key, project=project, name=name)

  @classmethod
  def get_branch_for_name(cls, project, name):
    key = 'p.%s %s' % (project.key().id(), name)
    return cls.get_by_key_name(key)

  @property
  def short_name(self):
    if self.name.startswith("refs/heads/"):
      return self.name[len("refs/heads/"):]
    return self.name

  def is_code_reviewed(self):
    return True

  def merge_patchset(self, patchset):
    """Add a patchset to the end of the branch's merge queue

    This method runs in an independent transaction.
    """
    ps_key = patchset.key()
    def trans(key):
      b = db.get(key)
      if not ps_key in b.to_merge:
        b.to_merge.append(ps_key)
        if b.status is None:
          b.status = 'NEEDS_MERGE'
          b.merge_submitted = datetime.datetime.now()
        b.put()
    db.run_in_transaction(trans, self.key())

  def begin_merge(self):
    """Lock this branch and start merging patchsets.

    This method runs in an independent transaction.
    """
    def trans(key):
      b = db.get(key)
      if b.status == 'NEEDS_MERGE':
        b.status = 'MERGING'
        b.merging.extend(b.waiting)
        b.merging.extend(b.to_merge)
        b.waiting = []
        b.to_merge = []
        b.put()
        return b.merging
      return []
    keys = db.run_in_transaction(trans, self.key())
    objs = db.get(keys)

    good = []
    torm = []
    for k, ps in zip(keys, objs):
      if ps and not ps.change.closed:
        good.append(ps)
      else:
        torm.append(k)

    if torm:
      def clear_branch(key):
        b = db.get(key)

        for ps_key in torm:
          if ps_key in b.merging:
            b.merging.remove(ps_key)

        if not good and b.status in ('MERGING', 'BUILDING'):
          if b.to_merge:
            b.status = 'NEEDS_MERGE'
          else:
            b.status = None

        b.put()
      db.run_in_transaction(clear_branch, self.key())
    return good

  def finish_merge(self, success, fail, defer):
    """Update our patchset lists with the results of a merge.

    This method runs in an independent transaction.
    """
    def trans(key):
      b = db.get(key)

      rm = []
      rm.extend(success)
      rm.extend(fail)
      for ps in rm:
        ps_key = ps.key()

        if ps_key in b.to_merge:
          b.to_merge.remove(ps_key)
        if ps_key in b.merging:
          b.merging.remove(ps_key)
        if ps_key in b.waiting:
          b.waiting.remove(ps_key)

      for ps in defer:
        ps_key = ps.key()

        if ps_key in b.to_merge:
          b.to_merge.remove(ps_key)
        if ps_key in b.merging:
          b.merging.remove(ps_key)
        if ps_key not in b.waiting:
          b.waiting.append(ps_key)

      b.put()
    db.run_in_transaction(trans, self.key())

  def merged(self, merged):
    """Updates the branch to include pending PatchSets.

    This method runs in an independent transaction.
    """
    def trans(key):
      b = db.get(key)

      for ps in merged:
        ps_key = ps.key()

        if ps_key in b.to_merge:
          b.to_merge.remove(ps_key)
        if ps_key in b.merging:
          b.merging.remove(ps_key)
        if ps_key in b.waiting:
          b.waiting.remove(ps_key)

      if b.status in ('MERGING', 'BUILDING'):
        if b.to_merge:
          b.status = 'NEEDS_MERGE'
        else:
          b.status = None
      b.put()
    db.run_in_transaction(trans, self.key())


### Revisions ###

class RevisionId(BackedUpModel):
  """A specific revision of a project."""

  project = db.ReferenceProperty(Project, required=True)
  id = db.StringProperty(required=True) # == key

  author_name = db.StringProperty()
  author_email = db.EmailProperty()
  author_when = db.DateTimeProperty()
  author_tz = db.IntegerProperty()

  committer_name = db.StringProperty()
  committer_email = db.EmailProperty()
  committer_when = db.DateTimeProperty()
  committer_tz = db.IntegerProperty()

  ancestors = db.StringListProperty() # other RevisionId.id
  message = db.TextProperty()

  patchset_key = db.StringProperty()
  def _get_patchset(self):
    try:
      return self._patchset_obj
    except AttributeError:
      k_str = self._patchset_key
      if k_str:
        self._patchset_obj = db.get(db.Key(k_str))
      else:
        self._patchset_obj = None
    return self._patchset_obj

  def _set_patchset(self, p):
    if p is None:
      self._patchset_key = None
      self._patchset_obj = None
    else:
      self._patchset_key = str(p.key())
      self._patchset_obj = p
  patchset = property(_get_patchset, _set_patchset)

  @classmethod
  def get_or_insert_revision(cls, project, id, **kw):
    key = 'p.%s %s' % (project.key().id(), id)
    return cls.get_or_insert(key, project=project, id=id, **kw)

  @classmethod
  def get_revision(cls, project, id):
    key = 'p.%s %s' % (project.key().id(), id)
    return cls.get_by_key_name(key)

  @classmethod
  def get_for_patchset(cls, patchset):
    """Get all revisions linked to a patchset.
    """
    return gql(cls, 'WHERE patchset_key = :1', str(patchset.key()))

  def add_ancestor(self, other_id):
    """Adds the other revision as an ancestor for this one.

    If the other rev is already in the list, does nothing.
    """
    if not other_id in self.ancestors:
      self.ancestors.append(other_id)

  def remove_ancestor(self, other_id):
    """Removes an ancestor previously stored.

    If the other rev is not already in the list, does nothing.
    """
    if other_id in self.ancestors:
      self.ancestors.remove(other_id)

  def get_ancestors(self):
    """Fully fetches all ancestors from the data store.
    """
    p_id = self.project.key().id()
    names = ['p.%s %s' % (p_id, i) for i in self.ancestors]
    return [r for r in RevisionId.get_by_key_name(names) if r]

  def get_children(self):
    """Obtain the revisions that depend upon this one.
    """
    return gql(RevisionId,
               'WHERE project = :1 AND ancestors = :2',
               self.project, self.id)

  def link_patchset(self, new_patchset):
    """Uniquely connect one patchset to this revision.

    Returns True if the passed patchset is the single patchset;
    False if another patchset has already been linked onto it.
    """
    def trans(self_key):
      c = db.get(self_key)
      if c.patchset is None:
        c.patchset = new_patchset
        c.put()
        return True
      return False
    return db.run_in_transaction(trans, self.key())


class BuildAttempt(BackedUpModel):
  """A specific build attempt."""

  branch = db.ReferenceProperty(Branch, required=True)
  revision_id = db.StringProperty(required=True)
  new_changes = db.ListProperty(db.Key) # PatchSet

  started = db.DateTimeProperty(auto_now_add=True)
  finished = db.BooleanProperty(default=False)
  success = db.BooleanProperty()


### Changes, PatchSets, Patches, DeltaContents, Comments, Messages ###

class Change(BackedUpModel):
  """The major top-level entity.

  It has one or more PatchSets as its descendants.
  """

  subject = db.StringProperty(required=True)
  description = db.TextProperty()
  owner = db.UserProperty(required=True)
  created = db.DateTimeProperty(auto_now_add=True)
  modified = db.DateTimeProperty(auto_now=True)
  reviewers = db.ListProperty(db.Email)
  claimed = db.BooleanProperty(default=False)
  cc = db.ListProperty(db.Email)
  closed = db.BooleanProperty(default=False)
  n_comments = db.IntegerProperty(default=0)
  n_patchsets = db.IntegerProperty(default=0)

  dest_project = db.ReferenceProperty(Project, required=True)
  dest_branch = db.ReferenceProperty(Branch, required=True)

  merge_submitted = db.DateTimeProperty()
  merged = db.BooleanProperty(default=False)

  emailed_clean_merge = db.BooleanProperty(default=False)
  emailed_missing_dependency = db.BooleanProperty(default=False)
  emailed_path_conflict = db.BooleanProperty(default=False)

  merge_patchset_key = db.StringProperty()
  def _get_merge_patchset(self):
    try:
      return self._merge_patchset_obj
    except AttributeError:
      k_str = self._merge_patchset_key
      if k_str:
        self._merge_patchset_obj = db.get(db.Key(k_str))
      else:
        self._merge_patchset_obj = None
    return self._merge_patchset_obj

  def _set_merge_patchset(self, p):
    if p is None:
      self._merge_patchset_key = None
      self._merge_patchset_obj = None
    else:
      self._merge_patchset_key = str(p.key())
      self._merge_patchset_obj = p
  merge_patchset = property(_get_merge_patchset, _set_merge_patchset)

  _is_starred = None

  @property
  def is_starred(self):
    """Whether the current user has this change starred."""
    if self._is_starred is not None:
      return self._is_starred
    account = Account.current_user_account
    self._is_starred = account is not None and self.key().id() in account.stars
    return self._is_starred

  def update_comment_count(self, n):
    """Increment the n_comments property by n.
    """
    self.n_comments += n

  @property
  def num_comments(self):
    """The number of non-draft comments for this change.

    This is almost an alias for self.n_comments, except that if
    n_comments is None, it is computed through a query, and stored,
    using n_comments as a cache.
    """
    return self.n_comments

  _num_drafts = None

  @property
  def num_drafts(self):
    """The number of draft comments on this change for the current user.

    The value is expensive to compute, so it is cached.
    """
    if self._num_drafts is None:
      account = Account.current_user_account
      if account is None:
        self._num_drafts = 0
      else:
        query = gql(Comment,
            'WHERE ANCESTOR IS :1 AND author = :2 AND draft = TRUE',
            self, account.user)
        self._num_drafts = query.count()
    return self._num_drafts

  def new_patchset(self, **kw):
    """Construct a new patchset for this change.
    """
    def trans(change_key):
      change = db.get(change_key)
      change.n_patchsets += 1
      id = change.n_patchsets
      change.put()

      patchset = PatchSet(change=change, parent=change, id=id, **kw)
      patchset.put()
      return patchset
    return db.run_in_transaction(trans, self.key())

  def set_review_status(self, user):
    """Gets or inserts the ReviewStatus object for the suppiled user."""
    return ReviewStatus.get_or_insert_status(self, user)

  def get_review_status(self, user=None):
    """Return the lgtm status for the given user if supplied.  All for this 
    change otherwise."""
    if user:
      # The owner must be checked separately because she automatically
      # approves / verifies her own change and there is no ReviewStatus
      # for that one.
      if user == self.owner:
        return []
      return ReviewStatus.get_status_for_user(self, user)
    else:
      return ReviewStatus.all_for_change(self)

  @classmethod
  def get_reviewer_status(cls, reviews):
    """Return a tuple of who has commented on the changes.

    The owner of the change is automatically added to the list

    Args:
      reviews  a list of ReviewStatus objects are returned from
               get_review_status().

    Returns:
      A map of the LGTM_CHOICES keys to users, plus the mapping
          verified_by --> the uesrs who verified it
    """
    result = {}
    for (k,v) in LGTM_CHOICES:
      result[k] = [r.user for r in reviews if r.lgtm == k]
    result["verified_by"] = [r.user for r in reviews if r.verified]
    return result

  @property
  def is_submitted(self):
    """Return true if the change has been submitted for merge.
    """
    return self.merge_submitted is not None

  def submit_merge(self, patchset):
    """Schedule a specific patchset of the change to be merged.
    """
    branch = self.dest_branch
    if not branch:
      raise InvalidSubmitMergeException, 'No branch defined'

    if self.merged:
      raise InvalidSubmitMergeException, 'Already merged'

    if self.is_submitted:
      raise InvalidSubmitMergeException, \
        "Already merging patch set %s" % self.merge_patchset.id

    branch.merge_patchset(patchset)
    self.merge_submitted = datetime.datetime.now()
    self.merge_patchset = patchset
    self.emailed_clean_merge = False
    self.emailed_missing_dependency = False
    self.emailed_path_conflict = False

  def unsubmit_merge(self):
    """Unschedule a merge of this change.
    """
    if self.merged:
      raise InvalidSubmitMergeException, 'Already merged'

    self.merge_submitted = None
    self.merge_patchset = None

  def set_reviewers(self, reviewers):
    self.reviewers = reviewers
    self.claimed = len(reviewers) != 0

  def user_can_edit(self, user):
    return (self.owner == user
            or self.dest_project.is_user_lead(user)
            or AccountGroup.is_user_admin(user))


class PatchSetFilenames(BackedUpModel):
  """A list of the file names in a PatchSet.

  This is a descendant of a PatchSet.
  """

  compressed_filenames = db.BlobProperty()

  @classmethod
  def _mc(cls, patchset):
    return MemCacheKey("PatchSet %s filenames" % patchset.key())

  @classmethod
  def store_compressed(cls, patchset, bin):
    cls(key_name = 'filenames',
        compressed_filenames = db.Blob(bin),
        parent = patchset).put()
    cls._mc(patchset).set(cls._split(bin))

  @classmethod
  def get_list(cls, patchset):
    def read():
      c = cls.get_by_key_name('filenames', parent = patchset)
      if c:
        return cls._split(c.compressed_filenames)
      names = patchset._all_filenames()
      bin = zlib.compress("\0".join(names).encode('utf_8'), 9)
      cls(key_name = 'filenames',
          compressed_filenames = db.Blob(bin),
          parent = patchset).put()
      return names
    return cls._mc(patchset).get(read)

  @classmethod
  def _split(cls, bin):
    tmp = zlib.decompress(bin).split("\0")
    return [s.decode('utf_8') for s in tmp]


class PatchSet(BackedUpModel):
  """A set of patchset uploaded together.

  This is a descendant of an Change and has Patches as descendants.
  """

  id = db.IntegerProperty(required=True)
  change = db.ReferenceProperty(Change, required=True) # == parent
  message = db.StringProperty()
  owner = db.UserProperty(required=True)
  created = db.DateTimeProperty(auto_now_add=True)
  modified = db.DateTimeProperty(auto_now=True)
  revision = db.ReferenceProperty(RevisionId, required=True)
  complete = db.BooleanProperty(default=False)

  _filenames = None

  @property
  def filenames(self):
    if self._filenames is None:
      self._filenames = PatchSetFilenames.get_list(self)
    return self._filenames

  def _all_filenames(self):
    last = ''
    names = []
    while True:
      list = gql(Patch,
                 'WHERE patchset = :1 AND filename > :2'
                 ' ORDER BY filename',
                 self, last).fetch(500)
      if not list:
        break
      for p in list:
        names.append(p.filename)
      last = list[-1].filename
    return names

  def revision_hash(self):
    return self.revision.id


class Message(BackedUpModel):
  """A copy of a message sent out in email.

  This is a descendant of an Change.
  """

  change = db.ReferenceProperty(Change, required=True)  # == parent
  subject = db.StringProperty()
  sender = db.EmailProperty()
  recipients = db.ListProperty(db.Email)
  date = db.DateTimeProperty(auto_now_add=True)
  text = db.TextProperty()


class CachedDeltaContent(object):
  """A fully inflated DeltaContent stored in memcache.
  """
  def __init__(self, dc):
    self.data_lines = dc.data_lines
    self.patch_lines = dc.patch_lines
    self.is_patch = dc.is_patch
    self.is_data = dc.is_data

  @property
  def data_text(self):
    if self.data_lines is None:
      return None
    return ''.join(self.data_lines)

  @property
  def patch_text(self):
    if self.patch_lines is None:
      return None
    return ''.join(self.patch_lines)

  @classmethod
  def get(cls, key):
    def load():
      dc = db.get(key)
      if dc:
        return cls(dc)
      return None
    return MemCacheKey('DeltaContent:%s' % key.name(),
                       compress = True).get(load)


def _apply_patch(old_lines, patch_name, dif_lines):
  new_lines = []
  chunks = patching.ParsePatchToChunks(dif_lines, patch_name)
  for tag, old, new in patching.PatchChunks(old_lines, chunks):
    new_lines.extend(new)
  return ''.join(new_lines)

def _blob_hash(data):
  m = hashlib.sha1()
  m.update("blob %d\0" % len(data))
  m.update(data)
  return m.hexdigest()


class DeltaContent(BackedUpModel):
  """Any content, such as for the old or new image of a Patch,
     or the patch data itself.

     These are stored as top-level entities.

     Key:
        Git blob SHA-1 of inflate(text)
      -or-
        Randomly generated name if this is a patch
  """

  text_z = db.BlobProperty(required=True)
  depth = db.IntegerProperty(default=0, required=True)
  base = db.SelfReferenceProperty()

  _data_lines = None
  _data_text = None
  _patch_text = None
  _patch_lines = None

  @classmethod
  def create_patch(cls, id, text_z):
    key_name = 'patch:%s' % id
    return cls.get_or_insert(key_name,
                             text_z = db.Blob(text_z),
                             depth = 0,
                             base = None)

  @classmethod
  def create_content(cls, id, text_z, base = None):
    """Create (or lookup and return an existing) content instance.

       Arguments:
        id:
          Git blob SHA-1 hash of the fully inflated content.
        text_z:
          If base is None this is the deflated content whose hash
          is id.

          If base is supplied this is a patch which when applied to
          base yields the content whose hash is id.
        base:
          The base content if text_z is a patch.
    """
    key_name = 'content:%s' % id
    r = cls.get_by_key_name(key_name)
    if r:
      return r

    if base is None:
      return cls.get_or_insert(key_name,
                               text_z = db.Blob(text_z),
                               depth = 0,
                               base = None)

    my_text = _apply_patch(base.data_lines,
                           id,
                           zlib.decompress(text_z).splitlines(True))
    cmp_id = _blob_hash(my_text)
    if id != cmp_id:
      raise DeltaPatchingException()

    if base.depth < MAX_DELTA_DEPTH:
      return cls.get_or_insert(key_name,
                               text_z = db.Blob(text_z),
                               depth = base.depth + 1,
                               base = base)
    return cls.get_or_insert(key_name,
                             text_z = db.Blob(zlib.compress(my_text)),
                             depth = 0,
                             base = None)

  @property
  def is_patch(self):
    return self._base or self.key().name().startswith('patch:')

  @property
  def is_data(self):
    return self.key().name().startswith('content:')

  @property
  def data_text(self):
    if self._data_text is None:
      if self._base:
        base = CachedDeltaContent.get(self._base)
        raw = _apply_patch(base.data_lines,
                           self.key().name(),
                           self.patch_lines)
      else:
        raw = zlib.decompress(self.text_z)
      self._data_text = raw
    return self._data_text

  @property
  def data_lines(self):
    if self._data_lines is None:
      self._data_lines = self.data_text.splitlines(True)
    return self._data_lines

  @property
  def patch_text(self):
    if not self.is_patch:
      return None
    if self._patch_text is None:
      self._patch_text = zlib.decompress(self.text_z)
    return self._patch_text

  @property
  def patch_lines(self):
    if not self.is_patch:
      return None
    if self._patch_lines is None:
      self._patch_lines = self.patch_text.splitlines(True)
    return self._patch_lines


class Patch(BackedUpModel):
  """A single patch, i.e. a set of changes to a single file.

  This is a descendant of a PatchSet.
  """

  patchset = db.ReferenceProperty(PatchSet, required=True)  # == parent
  filename = db.StringProperty(required=True)
  status = db.StringProperty(required=True)  # 'A', 'M', 'D'
  n_comments = db.IntegerProperty()

  old_data = db.ReferenceProperty(DeltaContent, collection_name='olddata_set')
  new_data = db.ReferenceProperty(DeltaContent, collection_name='newdata_set')
  diff_data = db.ReferenceProperty(DeltaContent, collection_name='diffdata_set')

  @classmethod
  def get_or_insert_patch(cls, patchset, filename, **kw):
    """Get or insert the patch for a specific file path.

    This method runs in an independent transaction.
    """
    m = hashlib.sha1()
    m.update(filename)
    key = 'z%s' % m.hexdigest()
    return cls.get_or_insert(key,
                             parent = patchset,
                             patchset = patchset,
                             filename = filename,
                             **kw)

  @classmethod
  def get_patch(cls, parent, id_str):
    if id_str.startswith('z'):
      return cls.get_by_key_name(id_str, parent=parent);
    else:
      return cls.get_by_id(int(id_str), parent=parent);

  @property
  def id(self):
    return str(self.key().id_or_name())

  @property
  def num_comments(self):
    """The number of non-draft comments for this patch.
    """
    return self.n_comments

  def update_comment_count(self, n):
    """Increment the n_comments property by n.
    """
    self.n_comments += n

  _num_drafts = None

  @property
  def num_drafts(self):
    """The number of draft comments on this patch for the current user.

    The value is expensive to compute, so it is cached.
    """
    if self._num_drafts is None:
      user = Account.current_user_account
      if user is None:
        self._num_drafts = 0
      else:
        query = gql(Comment,
                    'WHERE patch = :1 AND draft = TRUE AND author = :2',
                    self, user.user)
        self._num_drafts = query.count()
    return self._num_drafts

  def _data(self, name):
    prop = '_%s_CachedDeltaContent' % name
    try:
      c = getattr(self, prop)
    except AttributeError:
      # XXX Using internal knowledge about db package:
      # Key for ReferenceProperty 'foo' is '_foo'.

      data_key = getattr(self, '_%s_data' % name, None)
      if data_key:
        c = CachedDeltaContent.get(data_key)
        if data_key in ('diff', 'new') \
           and self._diff_data == self._new_data:
          self._diff_CachedDeltaContent = c
          self._new_CachedDeltaContent = c
        else:
          setattr(self, prop, c)
      else:
        c = None
        setattr(self, prop, c)
    return c

  @property
  def patch_text(self):
    """Get the patch converting old_text to new_text.
    """
    return self._data('diff').patch_text

  @property
  def patch_lines(self):
    """The patch_text split into lines, retaining line endings.
    """
    return self._data('diff').patch_lines

  @property
  def old_text(self):
    """Original version of the file text.
    """
    d = self._data('old')
    if d:
      return d.data_text
    return ''

  @property
  def old_lines(self):
    """The old_text split into lines, retaining line endings.
    """
    d = self._data('old')
    if d:
      return d.data_lines
    return []

  @property
  def new_text(self):
    """Get self.new_content
    """
    d = self._data('new')
    if d:
      return d.data_text
    return ''

  @property
  def new_lines(self):
    """The new_text split into lines, retaining line endings.
    """
    d = self._data('new')
    if d:
      return d.data_lines
    return []


class Comment(BackedUpModel):
  """A Comment for a specific line of a specific file.

  This is a descendant of a Patch.
  """

  patch = db.ReferenceProperty(Patch)  # == parent
  message_id = db.StringProperty()  # == key_name
  author = db.UserProperty()
  date = db.DateTimeProperty(auto_now=True)
  lineno = db.IntegerProperty()
  text = db.TextProperty()
  left = db.BooleanProperty()
  draft = db.BooleanProperty(required=True, default=True)

  def complete(self, patch):
    """Set the shorttext and buckets attributes."""
    # TODO(guido): Turn these into caching proprties instead.
    # TODO(guido): Properly parse the text into quoted and unquoted buckets.
    self.shorttext = self.text.lstrip()[:50].rstrip()
    self.buckets = [Bucket(text=self.text)]


class Bucket(BackedUpModel):
  """A 'Bucket' of text.

  A comment may consist of multiple text buckets, some of which may be
  collapsed by default (when they represent quoted text).

  NOTE: This entity is never written to the database.  See Comment.complete().
  """
  # TODO(guido): Flesh this out.

  text = db.TextProperty()


class ReviewStatus(BackedUpModel):
  """The information for whether a user has LGTMed or verified a change."""
  change = db.ReferenceProperty(Change, required=True)  # == parent
  user = db.UserProperty(required=True)
  lgtm = db.StringProperty()
  verified = db.BooleanProperty()

  @classmethod
  def get_or_insert_status(cls, change, user):
    key = '<%s>' % user.email
    return cls.get_or_insert(key,
                             change=change,
                             user=user,
                             parent=change)

  @classmethod
  def get_status_for_user(cls, change, user):
    key = '<%s>' % user.email
    return cls.get_by_key_name(key, parent=change)

  @classmethod
  def all_for_change(cls, change):
    return gql(ReviewStatus,
               'WHERE ANCESTOR IS :1',
               change).fetch(FETCH_MAX)


### Contributor License Agreements ###

class IndividualCLA:
  NONE = 0


### Accounts ###


class Account(BackedUpModel):
  """Maps a user or email address to a user-selected real_name, and more.

  Nicknames do not have to be unique.

  The default real_name is generated from the email address by
  stripping the first '@' sign and everything after it.  The email
  should not be empty nor should it start with '@' (AssertionError
  error is raised if either of these happens).

  Holds a list of ids of starred changes.  The expectation
  that you won't have more than a dozen or so starred changes (a few
  hundred in extreme cases) and the memory used up by a list of
  integers of that size is very modest, so this is an efficient
  solution.  (If someone found a use case for having thousands of
  starred changes we'd have to think of a different approach.)

  Returns whether a user is authorized to do lgtm or verify.
  For now, these authorization check methods do not test which repository
  the change is in.  This will change.
  """

  user = db.UserProperty(required=True)
  email = db.EmailProperty(required=True)  # key == <email>
  preferred_email = db.EmailProperty()

  created = db.DateTimeProperty(auto_now_add=True)
  modified = db.DateTimeProperty(auto_now=True)

  welcomed = db.BooleanProperty(default=False)
  real_name_entered = db.BooleanProperty(default=False)
  real_name = db.StringProperty()
  mailing_address = db.TextProperty()
  mailing_address_country = db.StringProperty()
  phone_number = db.StringProperty()
  fax_number = db.StringProperty()

  cla_verified = db.BooleanProperty(default=False)
  cla_verified_by = db.UserProperty()
  cla_verified_timestamp = db.DateTimeProperty() # the first time it's set
  individual_cla_version = db.IntegerProperty(default=IndividualCLA.NONE)
  individual_cla_timestamp = db.DateTimeProperty()
  cla_comments = db.TextProperty()

  default_context = db.IntegerProperty(default=DEFAULT_CONTEXT,
                                       choices=CONTEXT_CHOICES)
  stars = db.ListProperty(int)  # Change ids of all starred changes
  unclaimed_changes_projects = db.ListProperty(db.Key)

  # Current user's Account.  Updated by middleware.AddUserToRequestMiddleware.
  current_user_account = None

  def get_email(self):
    "Gets the email that this person wants us to use -- separate from login."
    if self.preferred_email:
      return self.preferred_email
    else:
      return self.email

  def get_email_formatted(self):
    return '"%s" <%s>' % (self.real_name, self.get_email())

  @classmethod
  def get_account_for_user(cls, user):
    """Get the Account for a user, creating a default one if needed."""
    email = user.email()
    assert email
    key = '<%s>' % email
    # Since usually the account already exists, first try getting it
    # without the transaction implied by get_or_insert().
    account = cls.get_by_key_name(key)
    if account is not None:
      return account
    real_name = user.nickname()
    if '@' in real_name:
      real_name = real_name.split('@', 1)[0]
    assert real_name
    return cls.get_or_insert(key, user=user, email=email, real_name=real_name)

  @classmethod
  def get_account_for_email(cls, email):
    """Get the Account for an email address, or return None."""
    assert email
    key = '<%s>' % email
    return cls.get_by_key_name(key)

  @classmethod
  def get_accounts_for_emails(cls, emails):
    """Get the Accounts for all email address.
    """
    return cls.get_by_key_name(map(lambda x: '<%s>' % x, emails))

  @classmethod
  def get_real_name_for_email(cls, email):
    """Get the real_name for an email address, possibly a default."""
    account = cls.get_account_for_email(email)
    if account is not None and account.real_name:
      return account.real_name
    real_name = email
    if '@' in real_name:
      real_name = real_name.split('@', 1)[0]
    assert real_name
    return real_name

  @classmethod
  def get_accounts_for_real_name(cls, real_name):
    """Get the list of Accounts that have this real_name."""
    assert real_name
    assert '@' not in real_name
    return list(gql(cls, 'WHERE real_name = :1', real_name))

  @classmethod
  def get_email_for_real_name(cls, real_name):
    """Turn a real_name into an email address.

    If the real_name is not unique or does not exist, this returns None.
    """
    accounts = cls.get_accounts_for_real_name(real_name)
    if len(accounts) != 1:
      return None
    return accounts[0].email

  _drafts = None

  @property
  def drafts(self):
    """A list of change ids that have drafts by this user.

    This is cached in memcache.
    """
    if self._drafts is None:
      if self._initialize_drafts():
        self._save_drafts()
    return self._drafts

  def update_drafts(self, change, have_drafts=None):
    """Update the user's draft status for this change.

    Args:
      change: an Change instance.
      have_drafts: optional bool forcing the draft status.  By default,
          change.num_drafts is inspected (which may query the datastore).

    The Account is written to the datastore if necessary.
    """
    dirty = False
    if self._drafts is None:
      dirty = self._initialize_drafts()
    id = change.key().id()
    if have_drafts is None:
      have_drafts = bool(change.num_drafts)  # Beware, this may do a query.
    if have_drafts:
      if id not in self._drafts:
        self._drafts.append(id)
        dirty = True
    else:
      if id in self._drafts:
        self._drafts.remove(id)
        dirty = True
    if dirty:
      self._save_drafts()

  def _initialize_drafts(self):
    """Initialize self._drafts from scratch.

    This mostly exists as a schema conversion utility.

    Returns:
      True if the user should call self._save_drafts(), False if not.
    """
    drafts = memcache.get('user_drafts:' + self.email)
    if drafts is not None:
      self._drafts = drafts
      return False
    # We're looking for the Change key id.  The ancestry of comments goes:
    # Change -> PatchSet -> Patch -> Comment.
    change_ids = set(comment.key().parent().parent().parent().id()
                    for comment in gql(Comment,
                                       'WHERE author = :1 AND draft = TRUE',
                                       self.user))
    self._drafts = list(change_ids)
    return True

  def _save_drafts(self):
    """Save self._drafts to memcache."""
    memcache.set('user_drafts:' + self.email, self._drafts, 3600)

  def can_lgtm(self):
    """Returns whether the user can lgtm a given change.
    
    For now returns true and doesn't take the change to check.
    """
    return True

  def can_verify(self):
    """Returns whether the user can verify a given change.

    For now returns true and doesn't take the change to check.
    """
    return True

  @classmethod
  def get_all_accounts(cls):
    """Return all accounts"""
    all = cls.all()
    all.order('real_name')
    return list(all)


### Group ###

AUTO_GROUPS = ['admin', 'submitters']

class AccountGroup(BackedUpModel):
  """A set of users.  Permissions are assigned to groups.
  
  There are some groups that can't be deleted -- like admin and all
  """
  
  name = db.StringProperty(required=True)
  comment = db.TextProperty(required=False)
  members = db.ListProperty(users.User)

  @classmethod
  def get_all_groups(cls):
    """Return all groups"""
    all = cls.all()
    all.order('name')
    return list(all)

  @property
  def is_auto_group(self):
    """These groups can't be deleted."""
    return self.name in AUTO_GROUPS

  @classmethod
  def create_groups(cls):
    for group_name in AUTO_GROUPS:
      def trans():
        g = cls(name=group_name, comment=(
            'Auto created %s group' % group_name))
        g.put()
      q = cls.gql('WHERE name=:name', name=group_name)
      if q.get() is None:
        db.run_in_transaction(trans)

  @classmethod
  def get_group_for_name(cls, name):
    return cls.gql('WHERE name=:name', name=name).get()

  def remove(self):
    """delete this group"""
    def trans(group):
      group.delete()
      # this will do the ON DELETE CASCADE once the users are in there
    db.run_in_transaction(trans, self)

  def put(self):
    if self.name in ['admin', 'submitters']:
      memcache.delete('group_members:%s' % self.name)
    BackedUpModel.put(self)

  @classmethod
  def _is_in_cached_group(cls, user, group):
    if not user:
      return False
    cache_key = 'group_members:%s' % group
    users = memcache.get(cache_key)
    if not users:
      g = AccountGroup.get_group_for_name(group)
      if not g:
        AccountGroup.create_groups()
        g = AccountGroup.get_group_for_name(group)
      if len(g.members) == 0:
        # if there are no users in this group, everyone is in this group
        # (helps with testing, upgrading and bootstrapping)
        # In prod this never really happens, so don't bother caching it
        return True
      users = [u.email() for u in g.members]
      memcache.set(cache_key, users)
    return user.email() in users

  @classmethod
  def is_user_admin(cls, user):
    return AccountGroup._is_in_cached_group(user, 'admin')

  @classmethod
  def is_user_submitter(cls, user):
    return AccountGroup._is_in_cached_group(user, 'submitters')
