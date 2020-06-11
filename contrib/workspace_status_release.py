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
#   --workspace_status_command="python ./contrib/workspace_status_release.py"
#
# to your bazel command. So for example instead of
#
#   bazel build release.war
#
# use
#
#   bazel build --workspace_status_command="python ./contrib/workspace_status_release.py" release.war
#
# . Alternatively, you can adjust `.bazelrc` accordingly.
#
# If the script exits with non-zero code, it's considered as a failure
# and the output will be discarded.

from __future__ import print_function
import os
import subprocess
import sys

ROOT = os.path.abspath(__file__)
while not os.path.exists(os.path.join(ROOT, 'WORKSPACE')):
    ROOT = os.path.dirname(ROOT)
CMD = ['git', 'describe', '--always', '--match', 'v[0-9].*', '--dirty']


def revision(directory, parent):
    try:
        os.chdir(directory)
        return subprocess.check_output(CMD).strip().decode("utf-8")
    except OSError as err:
        print('could not invoke git: %s' % err, file=sys.stderr)
        sys.exit(1)
    except subprocess.CalledProcessError as err:
        # ignore "not a git repository error" to report unknown version
        return None
    finally:
        os.chdir(parent)


print("STABLE_BUILD_GERRIT_LABEL %s" % revision(ROOT, ROOT))
for d in os.listdir(os.path.join(ROOT, 'plugins')):
    p = os.path.join('plugins', d)
    if os.path.isdir(p):
        v = revision(p, ROOT)
        print('STABLE_BUILD_%s_LABEL %s' % (os.path.basename(p).upper(),
                                            v if v else 'unknown'))
