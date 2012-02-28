#!/usr/bin/python
import sys
import re
import subprocess

"""
This script generates reformat the output of git log to the
format of release note.

Arguments:
since -- tag name
until -- tag name

Example Input:

   * <commit subject>
   +
   <commit message>

   Bug: issue 123
   Change-Id: <change id>
   Signed-off-by: <name>

Expected Output:

   * issue 123 <commit subject>
   +
   <commit message>
"""

if len(sys.argv) != 3:
    sys.exit('Usage: ' + sys.argv[0] + ' <since> <until>')
since_until = sys.argv[1] + '..' + sys.argv[2]
proc = subprocess.Popen(['git', 'log', '--reverse', '--no-merges',
                         since_until, "--format=* %s%n+%n%b"],
                         stdout=subprocess.PIPE,
                         stderr=subprocess.STDOUT,)

stdout_value = proc.communicate()[0]

#fin = open('gitlog')
fout = open('ReleaseNote', 'w')

subject = ""
message = []

for line in stdout_value.splitlines(True):

    if re.match('\* ', line) >= 0:
        # Write change log for a commit
        if subject != "":
            # Write subject
            fout.write(subject)

            # Write message lines
            if message != []:
                # Clear + from last line in commit message
                message[-1] = '\n'
            for m in message:
                fout.write(m)

        # Start new commit block
        message = []
        subject = line
        continue

    # Move issue number to subject line
    elif re.match('Bug: ', line) is not None:
        line = line.replace('Bug: ', '').replace('\n',' ')
        subject = subject[:2] + line + subject[2:]
    # Move issue number to subject line
    elif re.match('Issue: ', line) is not None:
        line = line.replace('Issue: ', 'issue ').replace('\n',' ')
        subject = subject[:2] + line + subject[2:]

    # Remove commit footers
    elif re.match(r'((\w+-)+\w+:)', line) is not None:
        continue

    else:
        if line == '\n':
            # Don't add extra blank line if last one is already blank
            if message[-1] != '+\n':
                message.append('+\n')
        else:
            message.append(line)
