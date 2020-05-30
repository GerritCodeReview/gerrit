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

def revision_with_match(match, all_refs=False):
    command = REVISION_CMD[:] # Copying, as adding elements should not change REVISION_CMD
    if match:
        command += ['--match', match + '*']
    if all_refs:
        command += ['--all', '--long']
    return run(command)

def revision(template=None):
    if template:
        # First, we turn a template like `v2.16.19-1-gec686a6352` into ['v2', '16', '19']
        parts = template.split('-')[0].split('.')
        # Then we use that list to match against tags in the current repo, starting with
        # longest matches.
        #
        # We do not consider matches of only the first part (E.g.: 'v2') as we'd most
        # likely get some match on a really old tag, which would prohibit matching on
        # branches, which would yield better results in this case.
        for length in range(len(parts),1,-1):
            start = '.'.join(parts[0:length])
            for variant in ['.', '']:
                ret = revision_with_match(start + variant + '*')
                if ret.startswith(start + variant):
                    return ret

        if template.startswith('v'):
            # Matching the template against tags did not give anything good, but template
            # looks like a version, so we try matching the template against branch names.
            match = re.match('v([0-9]+\.[0-9]+)\.', template)
            for ref_kind in ['', 'origin/', 'gerrit/']:
                branch = 'stable-' + match.group(1)
                ret = revision_with_match(ref_kind + branch, True)
                for cutoff in ['heads/', 'remotes/', ref_kind]:
                    if ret.startswith(cutoff):
                        ret = ret[len(cutoff):]
                if ret.startswith(branch):
                    return ret

    # None of the template based methods worked out, so we're falling back to a
    # generic version match.
    ret = revision_with_match('v[0-9].*')
    if ret.startswith('v'):
        return ret

    # Still no good tag, so we re-try without any matching
    return revision_with_match(None)


def run_plugin_workspace_status():
    workspace_status_script = os.path.join('tools', 'workspace_status.py')
    if os.path.isfile(workspace_status_script):
        for line in run(["python", workspace_status_script]).split('\n'):
            if re.search("^STABLE_[a-zA-Z0-9().:@/_ -]*$", line):
                print(line)

def print_stamps():
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

if __name__ == '__main__':
    print_stamps()
