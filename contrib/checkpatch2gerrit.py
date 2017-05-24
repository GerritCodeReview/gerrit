#!/usr/bin/env python
#
# Copyright (C) 2017 Advanced Micro Devices, Inc.  All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http:#www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
import sys
import re
import json
import argparse
import urllib2

def reviewInput():
    data = {}
    data['labels'] = {}
    data['labels']['Code-Review'] = 0

    #TODO use robot_comments when things are more mature
    data['comments'] = {}
    data['message'] = "checkpatch.pl feedback:"
    return data

def addFileCommentInput(c, path, message, line_num=None):
    if path not in c:
        c[path] = []
    comment = {}
    if line_num:
        comment['line'] = line_num
    comment['message'] = message

    c[path].append(comment)


def dump(a):
    for l in a:
        sys.stdout.write(l)

ext_line = re.compile("#\d+: FILE: ([^:]+):(\d+):")
ext_file = re.compile("#\d+: FILE: ([^:^\n]+)")
ext_commitmsg = re.compile("#\d+:\s*$")
def process_stack(s, r, score, err_on_warn):
    if re.match('^NOTE:', s[0]):
        r['message'] = r['message'] + "\n" + "\n".join(s)
    elif re.match('^total: \d.*', s[0]):
        r['message'] = r['message'] + "\n" + "\n".join(s)
    elif re.match('^(ERROR|WARNING)', s[0]):
        if err_on_warn or s[0][0] == 'E':
            r['labels']['Code-Review'] = score

        if s[0].startswith("ERROR: Missing Signed-off-by: line(s)"):
            addFileCommentInput(r['comments'], "/COMMIT_MSG", s[0].strip())
            return

        m = ext_line.match(s[1])
        if m:
            # Example
            # ERROR: that open brace { should be on the previous line
            # #213: FILE: drivers/gpu/drm/amd/display/dc/core/dc.c:61:
            addFileCommentInput(r['comments'], m.group(1), s[0].strip(), m.group(2))
            return

        m = ext_file.match(s[1])
        if m:
            # Example
            # ERROR: do not set execute permissions for source files
            # #15: FILE: drivers/gpu/drm/amd/amdkfd/kfd_device_queue_manager.c
            addFileCommentInput(r['comments'], m.group(1), s[0].strip())
            return

        m = ext_commitmsg.match(s[1])
        if m:
            addFileCommentInput(r['comments'], "/COMMIT_MSG", s[0].strip())
            return

        r['message'] = r['message'] + "\n" + "\n".join(s)
    else:
        dump(s)

def main():
    parser = argparse.ArgumentParser(description="""
    Convert checkpatch.pl output into Gerrit inline comment

    Example:
    $ checkpatch.pl ... | %s -c 12345 -p 1 -u user -w password
    """ % __file__, formatter_class=argparse.RawTextHelpFormatter)

    parser.add_argument("-s", "--server", dest="SERVER", required=False, default="https://somegerritserver/")
    parser.add_argument("-c", "--change", dest="CHANGE", required=True)
    parser.add_argument("-p", "--patchset", dest="PATCHSET", required=True)
    parser.add_argument("-r", "--review-score", type=int, choices=range(-2,1), dest="REVIEW_SCORE", required=False, default=-1)
    parser.add_argument("-e", "--error-on-warning", dest="ERR_ON_WARN", action='store_true')
    parser.add_argument("-u", "--user", dest="USER", required=True)
    parser.add_argument("-w", "--password", dest="PASSWORD", required=True)
    parser.set_defaults(ERR_ON_WARN=False)

    args = parser.parse_args()

    stack = []
    ri = reviewInput()

    for line in sys.stdin:
        if re.match('^(ERROR|WARNING|NOTE:)', line) and len(stack) > 0:
            process_stack(stack, ri, args.REVIEW_SCORE, args.ERR_ON_WARN)
            stack = []

        stack.append(line)

    if len(stack) > 0:
        process_stack(stack, ri, args.REVIEW_SCORE, args.ERR_ON_WARN)
        stack = []

    # Post comment
    mgr = urllib2.HTTPPasswordMgrWithDefaultRealm()
    mgr.add_password(None, args.SERVER, args.USER, args.PASSWORD)
    # may need to change to urllib2.HTTPBasicAuthHandler(mgr) for Gerrit 2.14
    opener = urllib2.build_opener(urllib2.HTTPDigestAuthHandler(mgr))
    request = urllib2.Request("%sa/changes/%s/revisions/%s/review" % (args.SERVER, args.CHANGE, args.PATCHSET),
                                data=json.dumps(ri),
                                headers={"Content-Type":'application/json'})
    response = opener.open(request)
    sys.stdout.write(response.read())

if __name__ == '__main__':
    main()
