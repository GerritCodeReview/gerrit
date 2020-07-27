#!/usr/bin/env python

# This is a variant of the `workspace_status.py` script that in addition to
# plain `git describe` implements a few heuristics to arrive at more to the
# point stamps for directories. But due to the implemented heuristics, it will
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
REVISION_CMD = ['git', 'describe', '--always', '--dirty']


def run(command):
    try:
        return subprocess.check_output(command).strip().decode("utf-8")
    except OSError as err:
        print('could not invoke %s: %s' % (command[0], err), file=sys.stderr)
        sys.exit(1)
    except subprocess.CalledProcessError:
        # ignore "not a git repository error" to report unknown version
        return None


def revision_with_match(pattern=None, prefix=False, all_refs=False,
                        return_unmatched=False):
    """Return a description of the current commit

    Keyword arguments:
    pattern    -- (Default: None) Use only refs that match this pattern.
    prefix     -- (Default: False) If True, the pattern is considered a prefix
                  and does not require an exact match.
    all_refs   -- (Default: False) If True, consider all refs, not just tags.
    return_unmatched -- (Default: False) If False and a pattern is given that
                  cannot be matched, return the empty string. If True, return
                  the unmatched description nonetheless.
    """

    command = REVISION_CMD[:]
    if pattern:
        command += ['--match', pattern + ('*' if prefix else '')]
    if all_refs:
        command += ['--all', '--long']

    description = run(command)

    if pattern and not return_unmatched and not description.startswith(pattern):
        return ''
    return description


def branch_with_match(pattern):
    for ref_kind in ['origin/', 'gerrit/', '']:
        description = revision_with_match(ref_kind + pattern, all_refs=True,
                                          return_unmatched=True)
        for cutoff in ['heads/', 'remotes/', ref_kind]:
            if description.startswith(cutoff):
                description = description[len(cutoff):]
        if description.startswith(pattern):
            return description
    return ''


def revision(template=None):
    if template:
        # We use the version `v2.16.19-1-gec686a6352` as running example for the
        # below comments. First, we split into ['v2', '16', '19']
        parts = template.split('-')[0].split('.')

        # Although we have releases with version tags containing 4 numbers, we
        # treat only the first three numbers for simplicity. See discussion on
        # Ib1681b2730cf2c443a3cb55fe6e282f6484e18de.

        if len(parts) >= 3:
            # Match for v2.16.19
            version_part = '.'.join(parts[0:3])
            description = revision_with_match(version_part)
            if description:
                return description

        if len(parts) >= 2:
            version_part = '.'.join(parts[0:2])

            # Match for v2.16.*
            description = revision_with_match(version_part + '.', prefix=True)
            if description:
                return description

            # Match for v2.16
            description = revision_with_match(version_part)
            if description.startswith(version_part):
                return description

            if template.startswith('v'):
                # Match for stable-2.16 branches
                branch = 'stable-' + version_part[1:]
                description = branch_with_match(branch)
                if description:
                    return description

    # None of the template based methods worked out, so we're falling back to
    # generic matches.

    # Match for master branch
    description = branch_with_match('master')
    if description:
        return description

    # Match for anything that looks like a version tag
    description = revision_with_match('v[0-9].', return_unmatched=True)
    if description.startswith('v'):
        return description

    # Still no good tag, so we re-try without any matching
    return revision_with_match()


# prints the stamps for the current working directory
def print_stamps_for_cwd(name, template):
    workspace_status_script = os.path.join(
        'tools', 'workspace_status_release.py')
    if os.path.isfile(workspace_status_script):
        # directory has own workspace_status_command, so we use stamps from that
        for line in run(["python", workspace_status_script]).split('\n'):
            if re.search("^STABLE_[a-zA-Z0-9().:@/_ -]*$", line):
                print(line)
    else:
        # directory lacks own workspace_status_command, so we create a default
        # stamp
        v = revision(template)
        print('STABLE_BUILD_%s_LABEL %s' % (name.upper(),
                                            v if v else 'unknown'))


# os.chdir is different from plain `cd` in shells in that it follows symlinks
# and does not update the PWD environment. So when using os.chdir to change into
# a symlinked directory from gerrit's `plugins` or `modules` directory, we
# cannot recover gerrit's directory. This prevents the plugins'/modules'
# `workspace_status_release.py` scripts to detect the name they were symlinked
# as (E.g.: it-* plugins sometimes get linked in more than once under different
# names) and to detect gerrit's root directory. To work around this problem, we
# mimic the `cd` of ordinary shells. By using this function, symlink information
# is preserved in the `PWD` environment variable (as it is for example also done
# in bash) and plugin/module `workspace_status_release.py` scripts can pick up
# the needed information from there.
def cd(absolute_path):
    os.environ['PWD'] = absolute_path
    os.chdir(absolute_path)


def print_stamps():
    cd(ROOT)
    gerrit_version = revision()
    print("STABLE_BUILD_GERRIT_LABEL %s" % gerrit_version)
    for kind in ['modules', 'plugins']:
        kind_dir = os.path.join(ROOT, kind)
        for d in os.listdir(kind_dir) if os.path.isdir(kind_dir) else []:
            p = os.path.join(kind_dir, d)
            if os.path.isdir(p):
                cd(p)
                name = os.path.basename(p)
                print_stamps_for_cwd(name, gerrit_version)


if __name__ == '__main__':
    print_stamps()
