#!/bin/bash
#
# This script converts Gerrit's documentation from AsciiDoc to markdown,
# then makes some small modifications to make the files work with the
# Jekyll Documentation template.

sidebar=""
for f in *.txt;do
topic=${f%.*}
echo "Starting conversion of $f...";
# Set the appropriate sidebar (left navigation).
if [[ $topic == cmd-* ]]; then
  sidebar="cmd_sidebar"
elif [[ $topic == rest-api-* ]]; then
  sidebar="restapi_sidebar"
elif [[ $topic == error-* ]]; then
  sidebar="errors_sidebar"
else
  sidebar="gerritdoc_sidebar"
fi

# Build the menu metadata.
m="sidebar: $sidebar\npermalink: $topic.html";
# Get the title of the topic.
line=$(head -n 1 $topic.txt);
# Convert the topic to Docbook XML.
asciidoc -b docbook $f &&
pandoc -f docbook -t commonmark $topic.xml -o $topic.md &&
# Increment headings. Otherwise, level 1 headings will not display.
sed -i 's/^\#/\#\#/' $topic.md;
# Add the title metadata.
sed -i "1s;^;${line}\n;" $topic.md;
sed -i 's/^=/\# /' $topic.md;
sed -i '0,/^# \(.*\)/s//---\ntitle: \"\1\"\n---/' $topic.md;
# Add the sidebar and permalink metadata.
sed -i "/^title: /a $m" $topic.md;
echo "done."
# Remove the index file, as we have a custom one for the web site.
if [[ $topic.md = "index.md" ]]; then
  echo "Removing duplicate index file."
  rm $topic.md
else
  mv $topic.md jekyll_website/pages/gerrit/
fi
rm $topic.xml
done
