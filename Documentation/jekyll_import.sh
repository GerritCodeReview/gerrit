#!/bin/bash
#
# This script converts Gerrit's documentation from AsciiDoc to markdown,
# then makes some small modifications to make the files work with the
# Jekyll Documentation template.

#find ../../../ -iregex ".*/.*\.txt" -not -path ../../../ -exec cp {} ./ \;
for f in *.txt;do
echo "Starting conversion of $f...";
# Build the menu metadata.
m="sidebar: gerritdoc_sidebar\npermalink: ${f%.*}.html";
# Get the title of the topic.
line=$(head -n 1 ${f%.*}.txt);
# Convert the topic to Docbook XML.
asciidoc -b docbook $f &&
pandoc -f docbook -t commonmark ${f%.*}.xml -o ${f%.*}.md &&
# Increment headings. Otherwise, level 1 headings will not display.
sed -i 's/^\#/\#\#/' ${f%.*}.md;
# Add the title metadata.
sed -i "1s;^;${line}\n;" ${f%.*}.md;
sed -i 's/^=/\# /' ${f%.*}.md;
sed -i '0,/^# \(.*\)/s//---\ntitle: \"\1\"\n---/' ${f%.*}.md;
# Add the sidebar and permalink metadata.
sed -i "/^title: /a $m" ${f%.*}.md;
echo "done."
# Remove the index file, as we have a custom one for the web site.
if [[ ${f%.*}.md = "index.md" ]]; then
  echo "Removing duplicate index file."
  rm ${f%.*}.md
else
  mv ${f%.*}.md jekyll_website/pages/gerrit/
fi
rm ${f%.*}.xml
done
