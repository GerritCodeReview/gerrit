#!/bin/sh
# Convert git log to asciidoc ChangeLog file.
# The input parameters are <since> and <until> parameters of git log
# Synopsis: gitlog2asciidoc.sh <since> <until>
# Example: gitlog2asciidoc.sh v2.2.2 HEAD

git log --reverse --no-merges $1..$2 --format='* %s%n+%n%b' > gitlog
python gitlog2asciidoc.py
rm gitlog
exit 0