#!/usr/bin/python
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

from optparse import OptionParser
import re
import sys

PAT_GERRIT = re.compile(r'^GERRIT')
PAT_INCLUDE = re.compile(r'^(include::.*)(\[\])$')
PAT_GET = re.compile(r'^get::([^ \t\n]*)')
PAT_TITLE = re.compile(r'^\.(.*)')
PAT_STARS = re.compile(r'^\*\*\*\*')

GERRIT_UPLINK = """

++++
<hr style=\"
  height: 2px;
  color: silver;
  margin-top: 1.2em;
  margin-bottom: 0.5em;
\">
++++

"""

GET_TITLE = '<div class="title">%s</div>'

GET_MACRO = """

++++
%s
<div class="content monospaced">
<a id=\"{0}\" onmousedown="javascript:
  var i = document.URL.lastIndexOf(\'/Documentation/\');
  var url = document.URL.substring(0, i) + \'{0}\';
  document.getElementById(\'{0}\').href = url;">
    GET {0} HTTP/1.0
</a>
</div>
++++

"""

opts = OptionParser()
opts.add_option('-o', '--out', help='output file')
opts.add_option('-s', '--src', help='source file')
opts.add_option('-x', '--suffix', help='suffix for included filenames')
options, _ = opts.parse_args()

try:
  out_file = open(options.out, 'w')
  src_file = open(options.src, 'r')
  last_line = ''
  ignore_next_line = False
  last_title = ''
  for line in src_file.xreadlines():
    if PAT_GERRIT.match(last_line):
      # Case of "GERRIT\n------" at the footer
      out_file.write(GERRIT_UPLINK)
      last_line = ''
    elif PAT_INCLUDE.match(line):
      # Case of 'include::<filename>'
      match = PAT_INCLUDE.match(line)
      out_file.write(last_line)
      last_line = match.group(1) + options.suffix + match.group(2) + '\n'
    elif PAT_STARS.match(line):
      if PAT_TITLE.match(last_line):
        # Case of the title in '.<title>\n****\nget::<url>\n****'
        match = PAT_TITLE.match(last_line)
        last_title = GET_TITLE % match.group(1)
      else:
        out_file.write(last_line)
        last_title = ''
    elif PAT_GET.match(line):
      # Case of '****\nget::<url>\n****' in rest api
      url = PAT_GET.match(line).group(1)
      out_file.write(GET_MACRO.format(url) % last_title)
      ignore_next_line = True
    elif ignore_next_line:
      # Handle the trailing '****' of the 'get::' case
      last_line = ''
      ignore_next_line = False
    else:
      out_file.write(last_line)
      last_line = line
  out_file.write(last_line)
  out_file.close()
except IOError as err:
  sys.stderr.write(
      "error while expanding %s to %s: %s" % (options.src, options.out, err))
  exit(1)
