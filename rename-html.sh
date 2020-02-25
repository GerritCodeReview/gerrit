html_filepath=$1

base_name=$(basename $1 ".html")
base_dir=$(dirname $1)

js_filename="$base_name.js"
js_filepath="$base_dir/$js_filename"

template_filename="$base_name_html.js"
template_filepath="$base_dir/$template_filename"

if test -f "$js_filepath"; then
  git mv $html_filepath $template_filepath
else
  git mv $html_filepath $js_filepath
fi


