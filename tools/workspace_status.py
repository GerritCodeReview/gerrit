#!/usr/bin/env python

# This script will be run by bazel when the build process starts to
# generate key-value information that represents the status of the
# workspace. The output should be like
#
# KEY1 VALUE1
# KEY2 VALUE2
#
# If the script exits with non-zero code, it's considered as a failure
# and the output will be discarded.

from __future__ import print_function
import os
import subprocess
import sys
import re

ROOT = os.path.abspath(__file__)
while not os.path.exists(os.path.join(ROOT, 'WORKSPACE')):
    ROOT = os.path.dirname(ROOT)
REVISION_CMD = ['git', 'describe', '--always', '--dirty']


def run(command):
    try:
        return subprocess.check_output(command).strip().decode("utf-8")
    except OSError as err:
        print('could not invoke %s: %s' % (command[0], err), file=sys.stderr)
        sys.exit(1)
    except subprocess.CalledProcessError as err:
        # ignore "not a git repository error" to report unknown version
        return None

def revision_with_match(match):
    command = REVISION_CMD[:] # Copying, as adding elements should not change REVISION_CMD
    if match:
        command += ['--match', match + '*']
    return run(command)

def revision(template=None):
    ret = None
    if template:
        # First, we turn a template like `v2.16.19-1-gec686a6352` into ['v2', '16', '19']
        parts = template.split('-')[0].split('.')
        # Then we use that list to match against tags in the current repo, starting with
        # longest matches.
        for length in range(len(parts),0,-1):
            start = '.'.join(parts[0:length])
            for variant in ['.', '']:
                if ret is None:
                    ret = revision_with_match(start + variant + '*')
                    if not ret.startswith(start + variant):
                        ret = None

    if ret is None:
        # None of the template based methods worked out, so we're falling back to a
        # generic version match.
        ret = revision_with_match('v[0-9].*')
        if not ret.startswith('v'):
            ret = None

    if ret is None:
        # Still no good tag, so we re-try without any matching
        ret = revision_with_match(None)
    return ret


def run_plugin_workspace_status():
    workspace_status_script = os.path.join('tools', 'workspace_status.py')
    if os.path.isfile(workspace_status_script):
        for line in run(["python", workspace_status_script]).split('\n'):
            if re.search("^STABLE_[a-zA-Z0-9. /_-]*$", line):
                print(line)

os.chdir(ROOT)
GERRIT_VERSION=revision()
print("STABLE_BUILD_GERRIT_LABEL %s" % GERRIT_VERSION)
for d in os.listdir(os.path.join(ROOT, 'plugins')):
    p = os.path.join(ROOT, 'plugins', d)
    if os.path.isdir(p):
        os.chdir(p)

        v = revision(GERRIT_VERSION)
        print('STABLE_BUILD_%s_LABEL %s' % (os.path.basename(p).upper(),
                                            v if v else 'unknown'))
        run_plugin_workspace_status()
