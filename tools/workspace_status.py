#!/usr/bin/env python3

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
import re
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


def jgit_label_from_workspace():
    # JGit is fetched via http_archive (no .git, so git describe cannot run).
    # Read the pinned SHA from the strip_prefix in WORKSPACE.
    try:
        with open(os.path.join(ROOT, 'WORKSPACE')) as f:
            m = re.search(r'strip_prefix\s*=\s*"jgit-([0-9a-f]{40})"', f.read())
        return m.group(1)[:12] if m else 'unknown'
    except OSError:
        return 'unknown'


print("STABLE_BUILD_GERRIT_LABEL %s" % revision(ROOT, ROOT))
print("STABLE_BUILD_JGIT_LABEL %s" % jgit_label_from_workspace())
for kind in ['modules', 'plugins']:
    kind_dir = os.path.join(ROOT, kind)
    for d in os.listdir(kind_dir):
        p = os.path.join(kind_dir, d)
        if os.path.isdir(p):
            v = revision(p, ROOT)
            print('STABLE_BUILD_%s_LABEL %s' % (os.path.basename(p).upper(),
                                                v if v else 'unknown'))
