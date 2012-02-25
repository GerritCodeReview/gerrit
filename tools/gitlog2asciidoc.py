import string, re, os, subprocess

# This script generates reformat the output of git log to the 
# format of release note.
#
# Example Input:
#
#    * <commit subject>
#    +
#    <commit message>
#
#    Bug: issue 123
#    Change-Id: <change id>
#    Signed-off-by: <name>
#
# Expected Output:
#
#    * issue 123 <commit subject>
#    +
#    <commit message>
#


fin = open('gitlog')
fout = open('ReleaseNote', 'w')

subject = ""
message = []

for line in fin:

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
    elif re.match('Bug: ', line) >= 0:
        subject = subject[:2] + line.replace('Bug:', '').replace('\n',' ') + subject[2:]

    # Move issue number to subject line
    elif re.match('Issue: ', line) >= 0:
        subject = subject[:2] + line.replace('Issue:', 'issue').replace('\n',' ') + subject[2:]

    # Omit Change-Id:
    elif re.match('Change-Id:', line) >= 0:
        continue
    # Omit Signed-off-by:
    elif re.match('Signed-off-by:', line) >= 0:
        continue 

    else:
        if line == '\n':
            # Don't add extra blank line if last one is already blank
            if message[-1] != '+\n':
                message.append('+\n')
        else:
            message.append(line)
