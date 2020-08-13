echo "Renaming files"
cd polygerrit-ui/app
allTsFiles=()
for f in "$@"
do
  dstName="$(dirname "$f")/$(basename -- "$f" .js).ts"
  echo "Renaming $f to $dstName"
  git mv "$f" "$dstName"
  allTsFiles+=( $dstName )
done

rulesdir=$(dirname "$0")/tools/js/eslint-rules

echo "Fixing files: Step 1:"
node ../../node_modules/eslint/bin/eslint.js ${allTsFiles[@]} --fix --rulesdir $rulesdir

echo "Fixing files: Step 2:"
node ../../node_modules/eslint/bin/eslint.js ${allTsFiles[@]} --fix --rulesdir $rulesdir --rule "polymer-element:2"

git commit -m "Rename files to preserve history" -m "Test\Eslint fail - this is expected."
git add ${allTsFiles[@]}
commitText=$(printf '* %s\n' "${allTsFiles[@]}")
git commit -m "Convert files to typescript" -m "The change converts the following files to typescript:" -m "$commitText"
