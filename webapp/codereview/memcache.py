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

"""Utility support for dealing with memcache."""

from google.appengine.api import memcache
from google.appengine.runtime import DeadlineExceededError

import cStringIO
import pickle
import zlib


class Key(object):
  def __init__(self, key, timeout=None, compress=False, incore=False):
    if compress:
      key += '(z)'
    self._key = key
    self._timeout = timeout
    self._compress = compress
    self._incore = incore
    self._value = None

  def get(self, compute=None):
    if self._incore and self._value is not None:
      return self._value

    try:
      r = memcache.get(self._key)
      if r and self._compress:
        r = pickle.load(cStringIO.StringIO(zlib.decompress(r)))
    except DeadlineExceededError:
      r = None

    if r is None and compute is not None:
      r = compute()
      if r is not None:
        self.set(r)
    elif self._incore:
      self._value = r
    return r

  def clear(self):
    try:
      self._value = None
      memcache.delete(self._key)
    except DeadlineExceededError:
      pass

  def set(self, value):
    if self._incore:
      self._value = value

    try:
      if self._compress:
        buf = cStringIO.StringIO()
        pickle.dump(value, buf, -1)
        value = zlib.compress(buf.getvalue())

      if self._timeout is None:
        memcache.set(self._key, value)
      else:
        memcache.set(self._key, value, self._timeout)
    except DeadlineExceededError:
      pass


class CachedDict(object):
  """A cache of zero or more memcache keys.  The dictionary of
     acquired keys is also cached locally in this process, but
     can be forcibly cleared by clear_local.
  """
  def __init__(self,
               prefix,
               timeout,
               compute_one = None,
               compute_multi = None):
    self._prefix = prefix
    self._timeout = timeout
    self._cache = {}
    self._prefetch = set()

    if compute_multi:
      self._compute = compute_multi
    elif compute_one:
      self._compute = lambda x: map(compute_one, x)
    else:
      raise ValueError, 'compute_one or compute_multi required'

  def prefetch(self, keys):
    for item_key in keys:
      if item_key not in self._cache:
        self._prefetch.add(item_key)

  def get_multi(self, keys):
    result = {}

    not_local = []
    for item_key in keys:
      try:
        result[item_key] = self._cache[item_key]
      except KeyError:
        not_local.append(item_key)

    if not_local:
      to_get = []
      to_get.extend(not_local)
      to_get.extend(self._prefetch)
      self._prefetch = set()

      try:
        cached = memcache.get_multi(to_get, self._prefix)
      except DeadlineExceededError:
        cached = {}

      to_compute = []
      for item_key in to_get:
        try:
          r = cached[item_key]
        except KeyError:
          to_compute.append(item_key)
          continue
        self._cache[item_key] = r
        result[item_key] = r

      if to_compute:
        to_cache = {}
        for item_key, r in zip(to_compute, self._compute(to_compute)):
          if r is not None:
            to_cache[item_key] = r
          self._cache[item_key] = r
          result[item_key] = r

        if to_cache:
          try:
            memcache.set_multi(to_cache, self._timeout, self._prefix)
          except DeadlineExceededError:
            pass
    return result

  def get(self, key):
    return self.get_multi([key])[key]

  def clear_local(self):
    self._cache = {}
    self._prefetch = set()

  def clear(self):
    try:
      memcache.delete_multi(self._prefix)
    except DeadlineExceededError:
      pass
    self.clear_local()
