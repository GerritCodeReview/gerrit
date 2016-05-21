load('//tools/bzl:gwt.bzl', 'gwt_module')

SRC = 'src/main/java/org/eclipse/jgit/'

gwt_module(
  name = 'client',
  srcs = [
    SRC + 'diff/Edit_JsonSerializer.java',
    SRC + 'diff/ReplaceEdit.java',
  ],
  gwt_xml = SRC + 'JGit.gwt.xml',
  deps = [
    ':Edit',
    '//lib/gwt:user',
    '//lib:gwtjsonrpc',
  ],
  visibility = ['//visibility:public'],
)

gwt_module(
  name = 'Edit',
  srcs = [':jgit_edit_src'],
  visibility = ['//visibility:public'],
)

genrule(
  name = 'jgit_edit_src',
  cmd = 'ROOT=$$PWD;' +
    'TMP=$$(mktemp -d);' +  
    'unzip -qd $$TMP $(location @jgit_src//file) ' +
    'org/eclipse/jgit/diff/Edit.java;' +
    'cd $$TMP;' +
    'zip -Dq $$ROOT/$@ org/eclipse/jgit/diff/Edit.java',
  tools = ['@jgit_src//file'],
  outs = ['edit.srcjar'],
)

java_library(
  name = 'server',
  srcs = [
    SRC + 'diff/EditDeserializer.java',
    SRC + 'diff/ReplaceEdit.java',
    SRC + 'internal/storage/file/WindowCacheStatAccessor.java',
    SRC + 'lib/ObjectIdSerialization.java',
  ],
  deps = [
    '//lib:gson',
    '//lib/jgit/org.eclipse.jgit:jgit',
  ],
  visibility = ['//visibility:public'],
)
