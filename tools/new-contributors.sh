#!/bin/bash

# Check arguments
if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <start-sha> <end-sha>"
  exit 1
fi

START_SHA=$1
END_SHA=$2

# Get authors before START_SHA
authors_before=$(git log --format='%ae' "$START_SHA" | sort | uniq)

# Get authors between START_SHA and END_SHA
authors_between=$(git log --format='%ae' "$START_SHA..$END_SHA" | sort | uniq)

# Find authors in between but not before
new_authors=$(comm -23 <(echo "$authors_between") <(echo "$authors_before"))

echo "New authors between $START_SHA and $END_SHA:"
echo "$new_authors"
