#!/usr/bin/env python
# -*- coding: utf-8 -*-

# The MIT License
#
# Copyright 2014 Sony Mobile Communications. All rights reserved.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.

""" Script to abandon stale changes from the review server.

Fetches a list of open changes that have not been updated since a
given age in months or years (default 6 months), and then abandons them.

Assumes that the user's credentials are in the .netrc file.  Supports
either basic or digest authentication.

Example to abandon changes that have not been updated for 3 months:

  ./abandon_stale --gerrit-url http://review.example.com/ --age 3months

Supports dry-run mode to only list the stale changes but not actually
abandon them.

Requires pygerrit2 (https://github.com/dpursehouse/pygerrit2).

"""

import logging
import optparse
import re
import sys

from pygerrit2.rest import GerritRestAPI
from pygerrit2.rest.auth import HTTPBasicAuthFromNetrc, HTTPDigestAuthFromNetrc


def _main():
    parser = optparse.OptionParser()
    parser.add_option('-g', '--gerrit-url', dest='gerrit_url',
                      metavar='URL',
                      default=None,
                      help='gerrit server URL')
    parser.add_option('-b', '--basic-auth', dest='basic_auth',
                      action='store_true',
                      help='use HTTP basic authentication instead of digest')
    parser.add_option('-n', '--dry-run', dest='dry_run',
                      action='store_true',
                      help='enable dry-run mode: show stale changes but do '
                           'not abandon them')
    parser.add_option('-a', '--age', dest='age',
                      metavar='AGE',
                      default="6months",
                      help='age of change since last update '
                           '(default: %default)')
    parser.add_option('-m', '--message', dest='message',
                      metavar='STRING', default=None,
                      help='Custom message to append to abandon message')
    parser.add_option('--branch', dest='branches', metavar='BRANCH_NAME',
                      default=[], action='append',
                      help='Abandon changes only on the given branch')
    parser.add_option('--exclude-branch', dest='exclude_branches',
                      metavar='BRANCH_NAME',
                      default=[],
                      action='append',
                      help='Do not abandon changes on given branch')
    parser.add_option('--project', dest='projects', metavar='PROJECT_NAME',
                      default=[], action='append',
                      help='Abandon changes only on the given project')
    parser.add_option('--exclude-project', dest='exclude_projects',
                      metavar='PROJECT_NAME',
                      default=[],
                      action='append',
                      help='Do not abandon changes on given project')
    parser.add_option('--owner', dest='owner',
                      metavar='USERNAME',
                      default=None,
                      action='store',
                      help='Only abandon changes owned by the given user')
    parser.add_option('-v', '--verbose', dest='verbose',
                      action='store_true',
                      help='enable verbose (debug) logging')

    (options, _args) = parser.parse_args()

    level = logging.DEBUG if options.verbose else logging.INFO
    logging.basicConfig(format='%(asctime)s %(levelname)s %(message)s',
                        level=level)

    if not options.gerrit_url:
        logging.error("Gerrit URL is required")
        return 1

    pattern = re.compile(r"^([\d]+)(month[s]?|year[s]?|week[s]?)")
    match = pattern.match(options.age)
    if not match:
        logging.error("Invalid age: %s", options.age)
        return 1
    message = "Abandoning after %s %s or more of inactivity." % \
        (match.group(1), match.group(2))

    if options.basic_auth:
        auth_type = HTTPBasicAuthFromNetrc
    else:
        auth_type = HTTPDigestAuthFromNetrc

    try:
        auth = auth_type(url=options.gerrit_url)
        gerrit = GerritRestAPI(url=options.gerrit_url, auth=auth)
    except Exception as e:
        logging.error(e)
        return 1

    logging.info(message)
    try:
        stale_changes = []
        offset = 0
        step = 500
        query_terms = ["status:new", "age:%s" % options.age]
        if options.branches:
            query_terms += ["branch:%s" % b for b in options.branches]
        elif options.exclude_branches:
            query_terms += ["-branch:%s" % b for b in options.exclude_branches]
        if options.projects:
            query_terms += ["project:%s" % p for p in options.projects]
        elif options.exclude_projects:
            query_terms = ["-project:%s" % p for p in options.exclude_projects]
        if options.owner:
            query_terms += ["owner:%s" % options.owner]
        query = "%20".join(query_terms)
        while True:
            q = query + "&n=%d&S=%d" % (step, offset)
            logging.debug("Query: %s", q)
            url = "/changes/?q=" + q
            result = gerrit.get(url)
            logging.debug("%d changes", len(result))
            if not result:
                break
            stale_changes += result
            last = result[-1]
            if "_more_changes" in last:
                logging.debug("More...")
                offset += step
            else:
                break
    except Exception as e:
        logging.error(e)
        return 1

    abandoned = 0
    errors = 0
    abandon_message = message
    if options.message:
        abandon_message += "\n\n" + options.message
    for change in stale_changes:
        number = change["_number"]
        try:
            owner = change["owner"]["name"]
        except:
            owner = "Unknown"
        subject = change["subject"]
        if len(subject) > 70:
            subject = subject[:65] + " [...]"
        change_id = change["id"]
        logging.info("%s (%s): %s", number, owner, subject)
        if options.dry_run:
            continue

        try:
            gerrit.post("/changes/" + change_id + "/abandon",
                        data='{"message" : "%s"}' % abandon_message)
            abandoned += 1
        except Exception as e:
            errors += 1
            logging.error(e)
    logging.info("Total %d stale open changes", len(stale_changes))
    if not options.dry_run:
        logging.info("Abandoned %d changes. %d errors.", abandoned, errors)

if __name__ == "__main__":
    sys.exit(_main())
