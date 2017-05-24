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
import subprocess

def reviewInput():
    data = {}
    data['labels'] = {}
    data['labels']['Code-Review'] = 0

    #TODO use robot_comments when things are more mature
    data['comments'] = {}
    data['message'] = "cpplint feedback:"
    return data

def addFileCommentInput(c, path, message, line_num=None):
    if path not in c:
        c[path] = []
    comment = {}
    if line_num:
        comment['line'] = line_num
    comment['message'] = message

    c[path].append(comment)

def main():
    parser = argparse.ArgumentParser(description="""
    Convert cpplint.py output into Gerrit inline comment

    Example:
    $ cpplint.py ... $(git diff-tree ... | tr '\\n' ' ') | %s -s gerritserver -a c0ffee -g .git -c 12345 -p 1 -u user -w password
    """ % __file__, formatter_class=argparse.RawTextHelpFormatter)

    parser.add_argument("-a", "--hash", dest="HASH", required=True)
    parser.add_argument("-g", "--git-dir", dest="GITDIR", required=False, default=".git")
    parser.add_argument("-s", "--server", dest="SERVER", required=False, default="https://somegerritserver/")
    parser.add_argument("-c", "--change", dest="CHANGE", required=True)
    parser.add_argument("-p", "--patchset", dest="PATCHSET", required=True)
    parser.add_argument("-r", "--review-score", type=int, choices=range(-2,1), dest="REVIEW_SCORE", required=False, default=-1)
    parser.add_argument("-u", "--user", dest="USER", required=True)
    parser.add_argument("-w", "--password", dest="PASSWORD", required=True)
    parser.add_argument("-o", "--output", choices=['REST','stdout'], dest="OUTPUT", required=False, default='stdout')

    args = parser.parse_args()

    changed_lines = {}
    flagged_lines = {}
    ri = reviewInput()

    ext_line = re.compile("([^:]+):(\d+):(.+)")
    for line in sys.stdin:
        m = ext_line.match(line)
        if m:
            #addFileCommentInput(ri['comments'], m.group(1), s[0].strip(), m.group(2))
            path = m.group(1)
            line_num = int(m.group(2))
            msg = m.group(3).strip()
            if path not in flagged_lines:
                flagged_lines[path] = {}
            flagged_lines[path][line_num] = msg
            continue

        ri['message'] = ri['message'] + "\n" + line.strip()

    for path in flagged_lines:
        changed_lines[path] = set()
        try:
            gitblame = subprocess.Popen(('git', '--git-dir', args.GITDIR, 'blame', '-p', args.HASH, '--', path), stdout=subprocess.PIPE)
            grephash = subprocess.check_output(('grep', args.HASH), stdin=gitblame.stdout)
            gitblame.wait()
            for line in grephash.strip().split('\n'):
                #<40-byte hex sha1> <sourceline> <resultline> <num_lines>
                #3dd47c0e1d32a9e2430e97277c26508ff9753524 265 585
                #3dd47c0e1d32a9e2430e97277c26508ff9753524 266 586
                changed_lines[path].add(int(line.split()[2]))
        except subprocess.CalledProcessError as e:
            # This is expected if there are no match on grep
            # Happens when commit contain only deletion, for example
            print e.output

    for path in changed_lines:
        for num in changed_lines[path].intersection(flagged_lines[path]):
            addFileCommentInput(ri['comments'], path, flagged_lines[path][num], num)

    if args.OUTPUT == 'stdout':
        sys.stdout.write(json.dumps(ri, indent=2))
    elif args.OUTPUT == 'REST':
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
