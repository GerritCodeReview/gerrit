#!/usr/bin/env python

from __future__ import print_function

import subprocess
import getopt
import sys


SSH_USER = 'bot'
SSH_HOST = 'localhost'
SSH_PORT = 29418
SSH_COMMAND = 'ssh %s@%s -p %d gerrit approve ' % (SSH_USER, SSH_HOST, SSH_PORT)
FAILURE_SCORE = '--code-review=-2'
FAILURE_MESSAGE = 'This commit message does not match the standard.' \
        + '  Please correct the commit message and upload a replacement patch.'
PASS_SCORE = '--code-review=0'
PASS_MESSAGE = ''

def main():
    change = None
    project = None
    branch = None
    commit = None
    patchset = None

    try:
        opts, args = getopt.getopt(sys.argv[1:], '', \
            ['change=', 'project=', 'branch=', 'commit=', 'patchset='])
    except getopt.GetoptError as err:
        print('Error: %s' % (err))
        usage()
        sys.exit(-1)

    for arg, value in opts:
        if arg == '--change':
            change = value
        elif arg == '--project':
            project = value
        elif arg == '--branch':
            branch = value
        elif arg == '--commit':
            commit = value
        elif arg == '--patchset':
            patchset = value
        else:
            print('Error: option %s not recognized' % (arg))
            usage()
            sys.exit(-1)

    if change == None or project == None or branch == None \
        or commit == None or patchset == None:
        usage()
        sys.exit(-1)

    command = 'git cat-file commit %s' % (commit)
    status, output = subprocess.getstatusoutput(command)

    if status != 0:
        print('Error running \'%s\'. status: %s, output:\n\n%s' % \
            (command, status, output))
        sys.exit(-1)

    commitMessage = output[(output.find('\n\n')+2):]
    commitLines = commitMessage.split('\n')

    if len(commitLines) > 1 and len(commitLines[1]) != 0:
        fail(commit, 'Invalid commit summary.  The summary must be ' \
            + 'one line followed by a blank line.')

    i = 0
    for line in commitLines:
        i = i + 1
        if len(line) > 80:
            fail(commit, 'Line %d is over 80 characters.' % i)

    passes(commit)

def usage():
    print('Usage:\n')
    print(sys.argv[0] + ' --change <change id> --project <project name> ' \
        + '--branch <branch> --commit <sha1> --patchset <patchset id>')

def fail( commit, message ):
    command = SSH_COMMAND + FAILURE_SCORE + ' -m \\\"' \
        + _shell_escape( FAILURE_MESSAGE + '\n\n' + message) \
        + '\\\" ' + commit
    subprocess.getstatusoutput(command)
    sys.exit(1)

def passes( commit ):
    command = SSH_COMMAND + PASS_SCORE + ' -m \\\"' \
        + _shell_escape(PASS_MESSAGE) + ' \\\" ' + commit
    subprocess.getstatusoutput(command)

def _shell_escape(x):
    s = ''
    for c in x:
        if c in '\n':
            s = s + '\\\"$\'\\n\'\\\"'
        else:
            s = s + c
    return s

if __name__ == '__main__':
    main()

