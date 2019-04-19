#!/usr/bin/env python
# Copyright (C) 2013 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import unittest
from util import resolve_url

class TestResolveUrl(unittest.TestCase):
  """ run to test:
    python -m unittest -v util_test
  """

  def testKnown(self):
    url = resolve_url('GERRIT:foo.jar', {})
    self.assertEqual(url, 'https://gerrit-maven.storage.googleapis.com/foo.jar')

  def testKnownRedirect(self):
    url = resolve_url('MAVEN_CENTRAL:foo.jar',
                      {'MAVEN_CENTRAL': 'http://my.company.mirror/maven2'})
    self.assertEqual(url, 'https://my.company.mirror/maven2/foo.jar')

  def testCustom(self):
    url = resolve_url('https://maven.example.com/release/foo.jar', {})
    self.assertEqual(url, 'https://maven.example.com/release/foo.jar')

  def testCustomRedirect(self):
    url = resolve_url('MAVEN_EXAMPLE:foo.jar',
                      {'MAVEN_EXAMPLE': 'https://maven.example.com/release'})
    self.assertEqual(url, 'https://maven.example.com/release/foo.jar')

if __name__ == '__main__':
  unittest.main()
