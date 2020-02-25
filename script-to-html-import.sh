filename=$(basename $2 ".js")
filedir=$(dirname $1)

html_filename="$filename.html"
html_filepath="$filedir/$html_filename"

if test -f "$html_filepath"; then
  echo "File $html_filepath already exists."
  exit 1
fi

echo "<script src=\"$2\"></script>" >$html_filepath
sed -i "s/<script src=\"$filename.js\"><\/script>/<link rel=\"import\" href=\".\/$html_filename\">/g" $1
