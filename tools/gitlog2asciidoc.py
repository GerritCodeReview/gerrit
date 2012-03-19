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

issues_only = False
if sys.argv[1] == '--issues' :
  issues_only = True
  sys.argv.pop(0)

issue_numbers_only = False
if sys.argv[1] == '--issue_numbers' :
  issue_numbers_only = True
  sys.argv.pop(0)

if len(sys.argv) != 3:
    sys.exit('Usage: ' + sys.argv[0] + ' [--issues|--issue_numbers] <since> <until>')
since_until = sys.argv[1] + '..' + sys.argv[2]
proc = subprocess.Popen(['git', 'log', '--reverse', '--no-merges',
                         since_until, "--format=* %s%n+%n%b"],
                         stdout=subprocess.PIPE,
                         stderr=subprocess.STDOUT,)

stdout_value = proc.communicate()[0]

subject = ""
message = []
is_issue = False

for line in stdout_value.splitlines(True):

    if re.match('\* ', line) >= 0:
        # Write change log for a commit
        if subject != "":
            # Write subject
            if (not issues_only or is_issue) and not issue_numbers_only:
                sys.stdout.write(subject)

            # Write message lines
            if message != []:
                # Clear + from last line in commit message
                message[-1] = '\n'
            for m in message:
                if (not issues_only or is_issue) and not issue_numbers_only:
                    sys.stdout.write(m)

        # Start new commit block
        message = []
        subject = line
        is_issue = False
        continue

    # Move issue number to subject line
    elif re.match('([bB][uU][gG]|[iI][sS][sS][uU][eE]):( [iI][sS][sS][uU][eE])? ', line) is not None:
        line = re.sub('([bB][uU][gG]|[iI][sS][sS][uU][eE]):( [iI][sS][sS][uU][eE])? ', 'issue ', line, re.I).replace('\n',' ')
        subject = subject[:2] + line + subject[2:]
        is_issue = True
        if issue_numbers_only:
             sys.stdout.write(re.sub('\\* issue ([0-9]*) .*', '\\1', subject))

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
