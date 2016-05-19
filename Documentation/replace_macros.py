#!/usr/bin/env python
# coding=utf-8
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
PAT_SEARCHBOX = re.compile(r'^SEARCHBOX')

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
<div class="listingblock">
%s
<div class="content">
<a id=\"{0}\" onmousedown="javascript:
  var i = document.URL.lastIndexOf(\'/Documentation/\');
  var url = document.URL.substring(0, i) + \'{0}\';
  document.getElementById(\'{0}\').href = url;">
    GET {0} HTTP/1.0
</a>
</div>
</div>
++++

"""

SEARCH_BOX = """

++++
<div style="
  position:fixed;
  top:0px;
  right:0px;
  text-align:
  right;
  padding-top:2px;
  padding-right:0.5em;
  padding-bottom:2px;">
<input size="40"
  style="line-height: 0.75em;font-size: 0.75em;"
  id="docSearch"
  type="text">
<button style="
  background:none!important;
  border:none;
  padding:0!important;
  vertical-align:bottom;
  font-family:'Open Sans','DejaVu Sans',sans-serif;
  font-size:0.8em;
  color:#1d4b8f;
  text-decoration:none;"
  type="button"
  id="searchBox">
  Search
</button>
<script type="text/javascript">
var f = function() {
  window.location = '../#/Documentation/' +
    encodeURIComponent(document.getElementById("docSearch").value);
}
document.getElementById("searchBox").onclick = f;
document.getElementById("docSearch").onkeypress = function(e) {
  if (13 == (e.keyCode ? e.keyCode : e.which)) {
    f();
  }
}
</script>
</div>
++++

"""

LINK_SCRIPT = """

++++
<script type="text/javascript">
    decorate(document.getElementsByTagName('h1'));
    decorate(document.getElementsByTagName('h2'));
    decorate(document.getElementsByTagName('h3'));
    decorate(document.getElementsByTagName('h4'));

    var divs = document.getElementsByTagName('div');
    var arr = new Array();
    var excluded = getExcludedIds();
    for(var i = 0; i < divs.length; i++) {
      var d = divs[i];
      var id = d.getAttribute('id');
      if (id != null && !(id in excluded)) {
        arr[arr.length] = d;
      }
    }
    decorate(arr);

    var anchors = document.getElementsByTagName('a');
    arr = new Array();
    for(var i = 0; i < anchors.length; i++) {
      var a = anchors[i];
      // if the anchor has no id there is no target to
      // which we can link
      if (a.getAttribute('id') != null) {
        // if the anchor is empty there is no content which
        // can receive the mouseover event, an empty anchor
        // applies to the element that follows, move the
        // element that follows into the anchor so that there
        // is content which can receive the mouseover event
        if (a.firstChild == null) {
          var next = a.nextSibling;
          if (next != null) {
            next.parentNode.removeChild(next);
            a.appendChild(next);
          }
        }
        arr[arr.length] = a;
      }
    }
    decorate(arr);

    function decorate(e) {
      for(var i = 0; i < e.length; i++) {
        e[i].onmouseover = function (evt) {
          var element = this;
          // do nothing if the link icon is currently showing
          var a = element.firstChild;
          if (a != null && a instanceof Element
              && a.getAttribute('id') == 'LINK') {
            return;
          }

          // if there is no id there is no target to link to
          var id = element.getAttribute('id');
          if (id == null) {
            return;
          }

          // create and show a link icon that links to this element
          a = document.createElement('a');
          a.setAttribute('id', 'LINK');
          a.setAttribute('href', '#' + id);
          a.setAttribute('style', 'position: absolute;'
              + ' left: ' + (element.offsetLeft - 16 - 2 * 4) + 'px;'
              + ' padding-left: 4px; padding-right: 4px;');
          var span = document.createElement('span');
          span.setAttribute('style', 'height: ' + element.offsetHeight + 'px;'
              + ' display: inline-block; vertical-align: baseline;'
              + ' font-size: 16px; text-decoration: none; color: grey;');
          a.appendChild(span);
          var link = document.createTextNode('🔗');
          span.appendChild(link);
          element.insertBefore(a, element.firstChild);

          // remove the link icon when the mouse is moved away,
          // but keep it shown if the mouse is over the element, the link or the icon
          hide = function(evt) {
            if (document.elementFromPoint(evt.clientX, evt.clientY) != element
                && document.elementFromPoint(evt.clientX, evt.clientY) != a
                && document.elementFromPoint(evt.clientX, evt.clientY) != span
                && document.elementFromPoint(evt.clientX, evt.clientY) != link
                && element.contains(a)) {
              element.removeChild(a);
            }
          }
          element.onmouseout = hide;
          a.onmouseout = hide;
          span.onmouseout = hide;
          link.onmouseout = hide;
        }
      }
    }

    function getExcludedIds() {
      var excluded = {};
      excluded['header'] = true;
      excluded['toc'] = true;
      excluded['toctitle'] = true;
      excluded['content'] = true;
      excluded['preamble'] = true;
      excluded['footer'] = true;
      excluded['footer-text'] = true;
      return excluded;
    }
</script>

++++

"""

opts = OptionParser()
opts.add_option('-o', '--out', help='output file')
opts.add_option('-s', '--src', help='source file')
opts.add_option('-x', '--suffix', help='suffix for included filenames')
opts.add_option('-b', '--searchbox', action="store_true", default=True,
                help="generate the search boxes")
opts.add_option('--no-searchbox', action="store_false", dest='searchbox',
                help="don't generate the search boxes")
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
    elif PAT_SEARCHBOX.match(last_line):
      # Case of 'SEARCHBOX\n---------'
      if options.searchbox:
        out_file.write(SEARCH_BOX)
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
  out_file.write(LINK_SCRIPT)
  out_file.close()
except IOError as err:
  sys.stderr.write(
      "error while expanding %s to %s: %s" % (options.src, options.out, err))
  exit(1)
