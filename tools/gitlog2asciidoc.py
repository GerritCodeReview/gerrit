#!/usr/bin/python
from optparse import OptionParser
import re
import subprocess
import sys

"""
This script generates a release note from the output of git log
between the specified tags.

Options:
--issues          Show output the commits with issues associated with them.
--issue_numbers   Show outputs issue numbers of the commits with issues
                  associated with them

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

parser = OptionParser(usage='Usage: gitlog2asciidoc.py '+
                            '[--issues|--issue_numbers] <since> <until>')

parser.add_option('-i', '--issues',
                  action='store_true',
                  dest='issues_only',
                  default=False,
                  help='only output the commits with issues associated with them')

parser.add_option('-n', '--issue_numbers',
                  action='store_true',
                  dest='issue_numbers_only',
                  default=False,
                  help='only outputs issue numbers of the commits with \
                        issues associated with them')

(options, args) = parser.parse_args()

issues_only = options.issues_only
issue_numbers_only = options.issue_numbers_only

if len(args) != 2:
    parser.error("wrong number of arguments")

since_until = args[0] + '..' + args[1]
proc = subprocess.Popen(['git', 'log', '--reverse', '--no-merges',
                         since_until, "--format=* %s%n+%n%b"],
                         stdout=subprocess.PIPE,
                         stderr=subprocess.STDOUT,)

stdout_value = proc.communicate()[0]

subject = ""
message = []
is_issue = False

for line in stdout_value.splitlines(True):

    p=re.compile('(issue )?bug: (gerrit-|issue )?|(bug )?issue: (bug )?',
                  re.IGNORECASE)

    if issue_numbers_only:
        if p.match(line):
            sys.stdout.write(p.sub('', line))

    else:
        # Move issue number to subject line
        if p.match(line):
            line = p.sub('issue ', line).replace('\n',' ')
            subject = subject[:2] + line + subject[2:]
            is_issue = True
            if issue_numbers_only:
                 sys.stdout.write(re.sub(r'\* issue ([0-9]*) .*', '\\1', subject))

        elif re.match('\* ', line) >= 0:
            # Write change log for a commit
            if subject != "":

                if (not issues_only or is_issue):
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
            is_issue = False
            continue

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
