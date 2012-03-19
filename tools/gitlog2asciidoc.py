#!/usr/bin/python
import sys
import re
import subprocess

"""
This script generates a release note from the output of git log
between the specified tags.

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

subject = ""
message = []

# regex pattern to match following cases such as Bug: 123, Issue Bug: 123, Bug: GERRIT-123,
# Bug: issue 123, Bug issue: 123, issue: 123, issue: bug 123
p = re.compile('(issue )?bug: (gerrit-|issue )?|(bug )?issue: (bug )?',
                  re.IGNORECASE)

for line in stdout_value.splitlines(True):

    # Move issue number to subject line
    if p.match(line):
        line = p.sub('issue ', line).replace('\n',' ')
        subject = subject[:2] + line + subject[2:]

    elif re.match('\* ', line) >= 0:
        # Write change log for a commit
        if subject != "":
            # Write subject
            sys.stdout.write(subject)

            # Write message lines
            if message != []:
                # Clear + from last line in commit message
                message[-1] = '\n'
            for m in message:
                sys.stdout.write(m)

        # Start new commit block
        message = []
        subject = line
        continue

    # Remove commit footers
    elif re.match(r'((\w+-)+\w+:)', line) is not None:
        continue

    else:
        if line == '\n':
            # Don't add extra blank line if last one is already blank
            if message != [] and message[-1] != '+\n':
                message.append('+\n')
        else:
            message.append(line)
