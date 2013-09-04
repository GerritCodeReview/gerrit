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
import os, re

PAT_GERRIT = re.compile('^GERRIT')
PAT_INCLUDE = re.compile('^(include::)(.*)$')
PAT_GET = re.compile('^get::([^ \t\n]*)')

opts = OptionParser()
opts.add_option('-o', '--out', help='output file')
opts.add_option('-s', '--src', help='source file')
opts.add_option('-p', '--prefix', help='prefix for included filenames')
args, _ = opts.parse_args()

out_file = open(args.out, 'w')
orig_file = open(args.src, 'r')
last_line = ''
ignore_next_line = False
for line in orig_file.xreadlines() :
  if PAT_GERRIT.match(last_line) :
    # Case of "GERRT\n------" at the footer
    out_file.write(
        '\n' +
        '++++\n' +
        '<hr style="\n' +
        '  height: 2px;\n' +
        '  color: silver;\n' +
        '  margin-top: 1.2em;\n' +
        '  margin-bottom: 0.5em;\n' +
        '">\n' +
        '++++\n')
    last_line = ''
  elif PAT_INCLUDE.match(line) :
    # Case of 'include::<filename>'
    match = PAT_INCLUDE.match(line)
    out_file.write(last_line)
    last_line = match.group(1) + args.prefix + match.group(2) + '\n'
  elif PAT_GET.match(line) :
    # Case of '****\nget::<url>\n****' in rest api
    url = PAT_GET.match(line).group(1)
    out_file.write(
        '\n' +
        '++++\n' +
        '<a id="' + url + '" onmousedown="javascript:\n' +
        '  var i = document.URL.lastIndexOf(\'/Documentation/\');\n' +
        '  var url = document.URL.substring(0, i) + \'' + url + '\';\n' +
        '  document.getElementById(\'' + url + '\').href = url;">\n' +
        '    GET ' + url + ' HTTP/1.0\n' +
        '</a>\n' +
        '++++\n')
    ignore_next_line = True
  elif ignore_next_line :
    # Handle the trailing '****' of the 'get::' case
    last_line = ''
    ignore_next_line = False
  else :
    out_file.write(last_line)
    last_line = line
out_file.write(last_line)
out_file.close()
