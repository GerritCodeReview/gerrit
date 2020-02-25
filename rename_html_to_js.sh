find ./ -name '*.html' -type f -not -path "*/node_modules/*" -exec sh -c 'echo {}' \n \; >$tmp_html_files
