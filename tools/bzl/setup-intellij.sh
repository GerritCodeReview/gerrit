#!/bin/sh

# This script sets up a 'bazel_external' libraries for maven jars and Auto classes.
#
# To use:
#
# * Start IntelliJ
# * Go to "project structure" (Ctrl-Alt-Shift-S),
# * Go to "Libraries",
# * Right click "bazel_external"
# * Select "Add to Module"
# * Select all modules, click OK
# * Click "Apply"

mkdir -p .idea/libraries/
dest=.idea/libraries/bazel_external.xml

cat <<EOF > $dest
 <component name="libraryTable">
  <library name="bazel_external">
    <CLASSES>
EOF

for jar in $(bazel query --nohost_deps --output=location 'kind(file,deps(kind(java_import,deps(//...))))' | grep 'source file .*jar$' | sed 's|/BUILD:[0-9]*:[0-9]*: source file [^:]*:|/|' ); do
cat <<EOF  >> $dest
      <root url="jar://$jar!/" />
EOF
done

cat <<EOF >> $dest
    </CLASSES>
    <JAVADOC />
    <SOURCES />
  </library>
</component>
EOF

dest=.idea/libraries/bazel_autogen.xml
cat <<EOF > $dest
<component name="libraryTable">
  <library name="bazel_autogen">
    <CLASSES />
    <JAVADOC />
    <SOURCES>
EOF

not_found=""

for dep in $(bazel query 'rdeps(//...,//lib/auto:auto-value,1) - //lib/auto:auto-value'); do
    root=$(echo $dep | sed 's|//\(.*\):\(.*\)|bazel-bin/\1/_javac/\2/lib\2_sourcegenfiles|g')
    if [[ ! -d $root ]]; then
        # for some reason, tests don't have the "lib" prefix.
        root=$(echo $dep | sed 's|//\(.*\):\(.*\)|bazel-bin/\1/_javac/\2/\2_sourcegenfiles|g')
    fi
    if [[ ! -d $root ]]; then
        not_found="$not_found $root"
    fi
cat <<EOF  >> $dest
      <root url="file://\$PROJECT_DIR\$/$root" />
EOF
done

cat <<EOF >> $dest
    </SOURCES>
  </library>
</component>
EOF


if [[ -n "$not_found" ]]; then
    echo "some generated roots were missing. Did you run 'bazel build' yet?"
fi
