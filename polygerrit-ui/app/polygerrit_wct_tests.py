# Copyright (C) 2015 The Android Open Source Project
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

from __future__ import print_function

import atexit
from distutils import spawn
import json
import os
import pkg_resources
import shlex
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile


def _write_wct_conf(root, exports):
  with open(os.path.join(root, 'wct.conf.js'), 'w') as f:
    f.write('module.exports = %s;\n' % json.dumps(exports))


def _wct_cmd():
  return ['wct'] + shlex.split(os.environ.get('WCT_ARGS', ''))


class PolyGerritWctTests(unittest.TestCase):

  # Should really be setUpClass/tearDownClass, but Buck's test runner doesn't
  # produce sane stack traces from those methods. There's only one test method
  # anyway, so just use setUp.

  def _check_wct(self):
    self.assertTrue(
        spawn.find_executable('wct'),
        msg='wct not found; try `npm install -g web-component-tester`')

  def _extract_resources(self):
    tmpdir = tempfile.mkdtemp()
    root = os.path.join(tmpdir, 'polygerrit')
    os.mkdir(root)

    tr = 'test_resources.zip'
    zip_path = os.path.join(tmpdir, tr)
    s = pkg_resources.resource_stream(__name__, tr)
    with open(zip_path, 'w') as f:
      shutil.copyfileobj(s, f)

    with zipfile.ZipFile(zip_path, 'r') as z:
      z.extractall(root)

    return tmpdir, root

  def test_wct(self):
    self._check_wct()
    tmpdir, root = self._extract_resources()

    cmd = _wct_cmd()
    print('Running %s in %s' % (cmd, root), file=sys.stderr)

    _write_wct_conf(root, {
      'suites': ['test'],
      'webserver': {
        'pathMappings': [
          {'/components/bower_components': 'bower_components'},
        ],
      },
      'plugins': {
        'local': {
          # For some reason wct tries to install selenium into its node_modules
          # directory on first run. If you've installed into /usr/local and
          # aren't running wct as root, you're screwed. Turning this option off
          # seems to still work, so there's that.
          'skipSeleniumInstall': True,
        },
        'sauce': {
          # Disabled by default in order to run local tests only.
          # Run it with (saucelabs.com account required; free for open source):
          # WCT_ARGS='--plugin sauce' buck test --no-results-cache --include web
          'disabled': True,
          'browsers': [
            'OS X 10.11/chrome',
            'Windows 10/chrome',
            'Linux/firefox',
            'OS X 10.11/safari',
            'Windows 10/microsoftedge',
          ],
        },
      },
    })

    print(root)
    raise 'boom'
    p = subprocess.Popen(cmd, cwd=root,
                         stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    out, err = p.communicate()
    sys.stdout.write(out)
    sys.stderr.write(err)
    self.assertEquals(0, p.returncode)

    # Only remove tmpdir if successful, to allow debugging.
    shutil.rmtree(tmpdir)


if __name__ == '__main__':
  unittest.main()
