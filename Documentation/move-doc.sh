#!/bin/bash
# Script that moves a document to a hierarchical path

DOCUMENT=$1
NEWDOC=$2
DIRPATH=$(dirname $NEWDOC)
FILENAME=$(basename $NEWDOC)

if [ ! -f $DOCUMENT ]; then
	echo "Error: Can't find $DOCUMENT"
	exit 1
fi

if [ ! -d $DIRPATH ]; then
	echo New dir: $DIRPATH
	mkdir -p $DIRPATH
fi

# Update all links in document to point relatively to root dir
I=$DIRPATH
RELDIR=
while [[ "${I}" != "." ]]; do
	I=$(dirname $I)
	RELDIR="../${RELDIR}"
done
cat $DOCUMENT | sed s#link:#link:${RELDIR}#  > $DOCUMENT.updated
if [ 0 == $? ]; then
	mv $DOCUMENT $DOCUMENT.orig && mv $DOCUMENT.updated $DOCUMENT && git add $DOCUMENT || echo Error: failed to update $DOCUMENT.
else
	echo Error: sed line failed for $DOCUMENT
fi

# Which files to update links in
for UPDATE in $(find . -name \*.txt | grep -v $DOCUMENT); do
	HTML_DOCUMENT=$(echo $DOCUMENT | sed 's/\.txt$/.html/')
	grep "link:$HTML_DOCUMENT" $UPDATE > /dev/null
	if [ 0 == $? ]; then
		# Document needs update
		echo Fix $UPDATE
		# Escaping HTML_DOCUMENT string for use with sed.
		ESC_HTML_DOCUMENT=$(echo $HTML_DOCUMENT | sed 's#\.#\\.#')
		# Working through file that needs update and replacing
		cat $UPDATE | sed s#link:${ESC_HTML_DOCUMENT}#link:${DIRPATH}/${HTML_DOCUMENT}#  > $UPDATE.updated
		if [ 0 == $? ]; then
			mv $UPDATE $UPDATE.orig && mv $UPDATE.updated $UPDATE && git add $UPDATE || echo Error: failed to update $UPDATE.
		else
			echo Error: sed line failed for $UPDATE
		fi
	fi
done

git mv $DOCUMENT $NEWDOC || echo Error: Failed to move $DOCUMENT to $NEWDOC
