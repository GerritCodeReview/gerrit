#!/usr/bin/python
import commands, getopt, sys

# To use this hook, have your patchset-created hook call it and
# pass along all options.  This hook uses ssh to review the patchset.
# It needs ssh access to gerrit.

SSH_USER = "bot"
FAILURE_SCORE = "--code-review=-2"
FAILURE_MESSAGE = "This commit message does not match the standard." \
        + "  Please correct the commit message and upload a replacement " \
        + "patch."
PASS_SCORE = "--code-review=0"
PASS_MESSAGE = ""

def main():
    change = None
    project = None
    branch = None
    commit = None
    patchset = None

    try:
        opts, args = getopt.getopt(sys.argv[1:], "", \
            ["change=", "project=", "branch=", "commit=", "patchset="])
    except getopt.GetoptError, err:
        print "Error: " + str(err)
        usage()
        sys.exit(-1)

    for arg, value in opts:
        if arg == "--change":
            change = value
        elif arg == "--project":
            project = value
        elif arg == "--branch":
            branch = value
        elif arg == "--commit":
            commit = value
        elif arg == "--patchset":
            patchset = value
        else:
            print "Error: option " + arg + " not recognized"
            usage()
            sys.exit(-1)

    if change == None or project == None or branch == None \
        or commit == None or patchset == None:
        usage()
        sys.exit(-1)

    command = "git cat-file commit " + commit
    status, output = commands.getstatusoutput(command)

    if status != 0:
        print "Error running '" + command + "'. status: " + str(status) \
            + ", output:\n\n" + output
        sys.exit(-1)

    commitMessage = output[(output.find("\n\n")+2):]
    commitLines = commitMessage.split("\n")

    if len(commitLines) > 1 and len(commitLines[1]) != 0:
        fail(commit, "Invalid commit summary.  The summary must be " \
            + "one line followed by a blank line.")

    i = 0
    for line in commitLines:
        i = i + 1
        if len(line) > 80:
            fail(commit, "Line " + str(i) + " is over 80 characters.")

    passes(commit)
 
def usage():
    print "Usage:\n"
    print sys.argv[0] + " --change <change id> --project <project name> " \
        + "--branch <branch> --commit <sha1> --patchset <patchset id>"

def fail( commit, message ):
    command = "ssh " + SSH_USER + "@localhost -p 29418 gerrit approve " \
	+ FAILURE_SCORE + " -m \\\"" + FAILURE_MESSAGE + "     " \
	+ message + "\\\" " + commit
    commands.getstatusoutput(command)
    sys.exit(1)

def passes( commit ):
    command = "ssh " + SSH_USER + "@localhost -p 29418 gerrit approve " \
	+ PASS_SCORE + " -m \\\"" + PASS_MESSAGE + " \\\" " + commit
    print "Running: " + command
    commands.getstatusoutput(command)

if __name__ == "__main__":
    main()

