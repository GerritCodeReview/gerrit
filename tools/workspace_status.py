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
REVISION_CMD = ['git', 'describe', '--always', '--match', 'v[0-9].*', '--dirty']


def run(command):
    try:
        return subprocess.check_output(command).strip().decode("utf-8")
    except OSError as err:
        print('could not invoke %s: %s' % (command[0], err), file=sys.stderr)
        sys.exit(1)
    except subprocess.CalledProcessError as err:
        # ignore "not a git repository error" to report unknown version
        return None


def revision():
    return run(REVISION_CMD)


def run_plugin_workspace_status():
    workspace_status_script = os.path.join('tools', 'workspace_status.py')
    if os.path.isfile(workspace_status_script):
        for line in run(["python", workspace_status_script]).split('\n'):
            if re.search("^STABLE_[a-zA-Z0-9. /_-]*$", line):
                print(line)

os.chdir(ROOT)
print("STABLE_BUILD_GERRIT_LABEL %s" % revision())
for d in os.listdir(os.path.join(ROOT, 'plugins')):
    p = os.path.join(ROOT, 'plugins', d)
    if os.path.isdir(p):
        os.chdir(p)

        v = revision()
        print('STABLE_BUILD_%s_LABEL %s' % (os.path.basename(p).upper(),
                                            v if v else 'unknown'))
        run_plugin_workspace_status()
