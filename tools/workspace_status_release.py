#!/usr/bin/env python

# This is a variant of the `workspace_status.py` script that in addition to
# plain `git describe` implements a few heuristics to arrive at more to the
# point stamps for plugins. But due to the implemented heuristics, it will
# typically take longer to run (especially if you use lots of plugins that
# come without tags) and might slow down your development cycle when used
# as default.
#
# To use it, simply add
#
#   --workspace_status_command="python ./tools/workspace_status_release.py"
#
# to your bazel command. So for example instead of
#
#   bazel build release.war
#
# use
#
#   bazel build --workspace_status_command="python ./tools/workspace_status_release.py" release.war
#
# Alternatively, you can add
#
#   build --workspace_status_command="python ./tools/workspace_status_release.py"
#
# to `.bazelrc` in your home directory.
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


def print_stamps_for_plugin(name):
    workspace_status_script = os.path.join('tools', 'workspace_status_release.py')
    if os.path.isfile(workspace_status_script):
        # plugin has own workspace_status_command, so we use stamps from that
        for line in run(["python", workspace_status_script]).split('\n'):
            if re.search("^STABLE_[a-zA-Z0-9().:@/_ -]*$", line):
                print(line)
    else:
        # plugin lacks own workspace_status_command no we create default stamp
        v = revision()
        print('STABLE_BUILD_%s_LABEL %s' % (name.upper(),
                                            v if v else 'unknown'))


os.chdir(ROOT)
print("STABLE_BUILD_GERRIT_LABEL %s" % revision())
for d in os.listdir(os.path.join(ROOT, 'plugins')):
    p = os.path.join(ROOT, 'plugins', d)
    if os.path.isdir(p):
        os.chdir(p)
        name = os.path.basename(p)
        print_stamps_for_plugin(name)
