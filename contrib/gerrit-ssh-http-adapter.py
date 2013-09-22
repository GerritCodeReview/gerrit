#!/usr/bin/python
# Copyright (C) 2013 Google, Inc.
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
"""Adapt legacy gerrit SSH clients to new HTTP interface.

This script acts as an adapter between Gerrit clients that only speak the
legacy SSH interface, and the new REST API. It is intended to be invoked
by sshd as 'gerrit', effectively emulating the SSH server running inside the
gerrit process.

For use with OpenSSH, set the ForceCommand variable in sshd_config to
run this script, with the URL to the Gerrit server as its only argument:

  ForceCommand /path/to/this/file https://gerrit.googlesource.com

Not all of the SSH commands are implemented. This script currently is
sufficient to generate patchset-created events for use with the Jenkins
Gerrit Trigger Plugin. One notable change in functionality under this
wrapper is that the query view does not include the patch's approval
state, because in the current REST API, these values seem to be available
only at the Change level, not on each Revision.
"""
import getopt
import json
import logging
import os
import sys
import time

import requests

LOG_FILE = '/var/log/gerrit-proxy'
LOG_FORMAT = '%(asctime)s %(process)5d %(levelname)-8s %(message)s'

LOG = logging.getLogger('gerrit-proxy')


class Usage(Exception):
    """Exceptions for incorrect command line arguments."""

    def __init__(self, msg):
        super(Usage, self).__init__()
        self.msg = msg


class DictAttrAdapterException(Exception):
    """Catch-all exceptions raised by the DictAttrAdapter class."""
    pass


class DictAttrAdapter(dict):
    """Makes items in the dictionary addressable as attributes."""

    def __getattr__(self, attr):
        try:
            return self.__getitem__(attr)
        except Exception as ex:
            raise DictAttrAdapterException(ex)

    def __setattr__(self, attr, val):
        try:
            return self.__setitem__(attr, val)
        except Exception as ex:
            raise DictAttrAdapterException(ex)


class Gerrit(object):
    """Abstracts calls to the Gerrit REST API."""
    GERRIT_JSON_PREFIX = ")]}'\n"

    def __init__(self, url):
        self._url = url

    def _json(self, response):
        """Convert JSON response to an object."""
        # Gerrit's JSON response can't be parsed by the JSON parser built into
        # Requests directly, because it includes a prefix for security reasons.
        return json.loads(response.text.lstrip(self.GERRIT_JSON_PREFIX),
                          object_hook=DictAttrAdapter)

    def _get(self, path, params=None):
        """Issue a GET to a Gerrit REST endpoint."""
        url = self._url + path
        if params is None:
            params = {}
        LOG.info('GET %s', url)
        response = requests.get(url, params=params)
        try:
            return self._json(response)
        except:
            LOG.error('Gerrit GET failed. url: %r, params: %r, response: %r',
                      url, params, response.text)
            raise

    def _post(self, path, data=None):
        """Issue a POST to a Gerrit REST endpoint."""
        url = self._url + path
        if data:
            data = json.dumps(data)
        headers = {'Content-type': 'application/json'}
        response = requests.post(url, data=data, headers=headers)
        try:
            return self._json(response)
        except:
            LOG.error('Gerrit POST failed. url: %r, data: %r, response: %r',
                      url, data, response.text)
            raise

    def changes(self, query=None, _params=None):
        """Get Gerrit changes matching a query."""
        # [u'status', u'kind', u'created', u'change_id', u'updated', u'project',
        #  u'_sortkey', u'mergeable', u'branch', u'owner', u'subject', u'id',
        #  u'_number']
        result = []
        more = True
        sortkey = None
        params = {'n': 50, 'q': query}
        if _params is not None:
            params.update(_params)
        while more:
            params['N'] = sortkey
            batch = self._get('/a/changes/', params=params)
            if batch:
                # suppress protection warning for _more_changes, _sortkey
                # pylint: disable=W0212
                result.extend(batch)
                more = '_more_changes' in batch[-1] and batch[-1]._more_changes
                sortkey = batch[-1]._sortkey
            else:
                break
        return result

    def change(self, change_id, params=None):
        """Get a single change by id."""
        if params is None:
            params = {}
        return self._get('/a/changes/%s/detail' % change_id, params=params)

    def projects(self):
        """Get a list of projects."""
        return self._get('/a/projects/')

    def create_review(self, change_id, revision_id, message, labels=None,
                      notify=None):
        """Post a review comment."""
        payload = {
            'message': message
        }
        if labels:
            payload.update({'labels': labels})
        if notify:
            payload.update({'notify': notify})
        url = '/a/changes/%s/revisions/%s/review' % (change_id, revision_id)
        return self._post(url, payload)


def _map_gerrit_time_to_epoch(gerrit_time):
    """Takes a time string from gerrit and maps it to epoch time"""

    gerrit_time_utc = gerrit_time[:26] + ' UTC'
    datetimeval = time.strptime(gerrit_time_utc, '%Y-%m-%d %H:%M:%S.%f %Z')
    epoch_time = time.mktime(datetimeval)
    return int(epoch_time)


def _report_account(account):
    """Creates a legacy account object."""
    return {
        'name': account.name,
        'email': account.email,
    }


def _report_patchset(sha, patch):
    """Creates a legacy patchSet object."""
    # suppress protection warning for _number
    # pylint: disable=W0212

    # Mapping to:
    #   [u'createdOn', u'number', u'parents', u'ref', u'revision',
    #    u'uploader']
    report = {
        'number': patch._number,
        # 'parents'
        'revision': sha,
        'ref': patch.fetch.values()[0].ref,
    }
    if 'commit' in patch:
        commit_date = patch.commit.committer.date
        report.update({
            'createdOn': _map_gerrit_time_to_epoch(commit_date),
            'uploader': _report_account(patch.commit.committer)
        })
    return report


def _report_approval(change):
    """Creates a legacy approval object."""
    # type, description, value, grantedOn, by
    report = []
    for description, approvals in change.labels:
        if description == 'Verified':
            typ = 'VRIF'
        elif description == 'Code-Review':
            typ = 'CRVW'
        else:
            continue
        for approval in approvals.all:
            report.append({
                'type': typ,
                'description': description,
                'value': approval.value,
                'grantedOn': _map_gerrit_time_to_epoch(approval.date),
                'by': _report_account(approval)
                })
    return report


def _report_change(change, gerrit_url):
    """Creates a legacy change object."""
    # Mapping to:
    #   [u'branch', u'createdOn', u'currentPatchSet', u'id', u'lastUpdated',
    #    u'number', u'open', u'owner', u'patchSets', u'project', u'sortKey',
    #    u'status', u'subject', u'url']

    # suppress protection warning for _number
    # pylint: disable=W0212
    report = {
        'project': change.project,
        'branch': change.branch,
        'id': change.change_id,
        'number': change._number,
        'subject': change.subject,
        'owner': change.owner,
        'url': gerrit_url + '/#/c/%s/' % change._number,
        'lastUpdated': _map_gerrit_time_to_epoch(change.updated),
        'sortKey': change._sortkey,
        # 'open'
        'status': change.status,
    }
    if 'revisions' in change:
        current_sha = change.current_revision
        current_patch = change.revisions[current_sha]
        report['currentPatchSet'] = _report_patchset(current_sha, current_patch)
        report['patchSets'] = []
        for sha, patch in change.revisions.iteritems():
            report['patchSets'].append(_report_patchset(sha, patch))
        report['patchSets'] = sorted(report['patchSets'],
                                     key=lambda x: x['number'],
                                     reverse=True)
    return report


class StreamEvents(object):
    """Simulate 'gerrit stream-events'."""

    def __init__(self, url):
        self._url = url
        self._gerrit = Gerrit(url)
        self._changes = {}
        self._patches = {}

        LOG.info('StreamEvents initializing')
        # Prime the caches with the current state of the world
        for change in self._new_changes():
            for _ in self._new_patches(change):
                pass
        LOG.info('StreamEvents ready')

    def _new_changes(self):
        """Returns an iterable of changes that have been updated since last
           time."""
        # Results are returned in reverse chronological order on update time. So
        # once we've seen a "new" change with an update time we've seen before,
        # we're done.
        for change in self._gerrit.changes():
            if change.id in self._changes:
                last_seen = self._changes[change.id]
                if change.updated == last_seen.updated:
                    break
                yield change
            else:
                yield change
            self._changes[change.id] = change

    def _new_patches(self, change):
        """Returns an iterable of patches that have not been seen."""
        patches = self._patches.setdefault(change.id, {})
        params = {'o': ['ALL_REVISIONS']}
        change = self._gerrit.change(change.id, params)
        for sha, data in change.revisions.iteritems():
            if sha not in patches:
                patches[sha] = data
                yield (sha, data)

    def poll(self):
        """Poll once for new changes."""
        for change in self._new_changes():
            for sha, data in self._new_patches(change):
                event = {
                    'type': 'patchset-created',
                    'change': _report_change(change, self._url),
                    'patchSet': _report_patchset(sha, data)
                }
                print json.dumps(event)
        sys.stdout.flush()

    def run(self, unused_argv):
        """Poll forever"""
        while True:
            self.poll()
            time.sleep(30)


class Query(object):
    """Simulate 'gerrit query'."""
    SHORT_OPTIONS = None
    LONG_OPTIONS = ['format=', 'current-patch-set', 'patch-sets',
                    'all-approvals']

    def __init__(self, url):
        self._url = url
        self._gerrit = Gerrit(url)

    def run(self, argv):
        """Execute the query described by the command line"""

        start = time.time()
        try:
            # Convert the query from the command line options to the REST
            # parameters
            options, args = getopt.getopt(argv, self.SHORT_OPTIONS,
                                          self.LONG_OPTIONS)
            query_options = ['DETAILED_ACCOUNTS', 'DETAILED_LABELS',
                             'ALL_COMMITS']
            for key, val in options:
                if key == '--format':
                    if val != 'JSON':
                        raise Usage('Unrecognized --format %r' % val)
                elif key == '--current-patch-set':
                    query_options.append('CURRENT_REVISION')
                elif key == '--patch-sets' or key == '--all-approvals':
                    query_options.append('ALL_REVISIONS')
                else:
                    assert False

            # Issue the query
            query_params = {}
            if query_options:
                query_params['o'] = query_options
            query = ' '.join(args).strip('"')
            result = self._gerrit.changes(query, query_params)
        except getopt.error, msg:
            raise Usage(msg)

        # Return the result
        elapsed = int(1000 * (time.time() - start))
        rows = len(result)
        for row in result:
            print json.dumps(_report_change(row, self._url))
        print json.dumps({
            'type': 'stats',
            'rowCount': rows,
            'runTimeMilliseconds': elapsed
        })


class LsProjects(object):
    """Simulate 'gerrit ls-projects'."""
    def __init__(self, url):
        self._gerrit = Gerrit(url)

    def run(self, unused_argv):
        """Return a list of projects, one per line."""
        for project in sorted(self._gerrit.projects()):
            print project


class Approve(object):
    """Simulate 'gerrit approve'."""
    SHORT_OPTIONS = ''
    LONG_OPTIONS = ['message=', 'notify=', 'verified=', 'code-review=']

    def __init__(self, url):
        self._gerrit = Gerrit(url)

    def run(self, argv):
        """Post the votes/message passed on the command line"""
        try:
            # Convert the query from the command line options to the REST
            # parameters
            options, args = getopt.gnu_getopt(argv, self.SHORT_OPTIONS,
                                              self.LONG_OPTIONS)
            assert len(args) == 1
            change_num, patch_num = [int(x) for x in args[0].split(',')]

            message = None
            labels = {}
            notify = None
            for key, val in options:
                if key == '--message':
                    message = val
                elif key == '--notify':
                    notify = val
                elif key == '--verified':
                    labels['Verified'] = val
                elif key == '--code-review':
                    labels['Code-Review'] = val
                else:
                    assert False, key

        except getopt.error, msg:
            raise Usage(msg)

        # suppress protection warning for _number
        # pylint: disable=W0212

        # Need to map the change,patch number pair passed on the command line
        # to the change id and sha expected by the REST API.
        params = {'o': ['ALL_REVISIONS']}
        change = self._gerrit.change(change_num, params)
        sha = None
        for sha, data in change.revisions.iteritems():
            if data._number == patch_num:
                break
        assert change.revisions[sha]._number == patch_num

        # Publish the review
        self._gerrit.create_review(change.id, sha, message, labels, notify)


def main(argv=None, gerrit_url=None):
    """Entry Point"""

    using_ssh = False
    if argv is None:
        if 'SSH_ORIGINAL_COMMAND' in os.environ:
            import shlex
            argv = shlex.split(os.environ['SSH_ORIGINAL_COMMAND'])
            using_ssh = True
            gerrit_url = sys.argv[1]
        else:
            argv = sys.argv

    if using_ssh:
        logging.basicConfig(filename=LOG_FILE, format=LOG_FORMAT)
    else:
        logging.basicConfig(format=LOG_FORMAT)
    logging.getLogger().setLevel(logging.INFO)

    try:
        # Handle the no-argument case
        if len(argv) < 2:
            print __doc__
            sys.exit(0)

        if gerrit_url is None:
            gerrit_url = argv[1]
            argv = argv[1:]

        if argv[1] == 'gerrit':
            argv = argv[1:]

        LOG.info(argv)
        if argv[1] == 'stream-events':
            StreamEvents(gerrit_url).run(argv[2:])
        elif argv[1] == 'query':
            Query(gerrit_url).run(argv[2:])
        elif argv[1] == 'ls-projects':
            LsProjects(gerrit_url).run(argv[2:])
        elif argv[1] == 'version':
            # This is the version of gerrit being emulated. 2.5.4 is somewhat
            # old now, but is the version I have to test against.
            print 'gerrit version 2.5.4'
        elif argv[1] == 'approve' or argv[1] == 'review':
            Approve(gerrit_url).run(argv[2:])
        else:
            raise Usage('Unrecognized command %r' % argv[1])

    except Usage, err:
        print >> sys.stderr, err.msg
        print >> sys.stderr, 'for help use --help'
        return 2

    except:
        LOG.exception('Caught exception')
        raise

if __name__ == '__main__':
    sys.exit(main())
