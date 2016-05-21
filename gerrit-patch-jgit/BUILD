load('//tools/bzl:genrule2.bzl', 'genrule2')
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

genrule2(
  name = 'jgit_edit_src',
  cmd = ' && '.join([
    'unzip -qd $$TMP $(location @jgit_src//file) ' +
      'org/eclipse/jgit/diff/Edit.java',
    'cd $$TMP',
    'zip -Dq $$ROOT/$@ org/eclipse/jgit/diff/Edit.java',
  ]),
  tools = ['@jgit_src//file'],
  out = 'edit.srcjar',
)

java_library(
  name = 'server',
  srcs = [
    SRC + x for x in [
      'diff/EditDeserializer.java',
      'diff/ReplaceEdit.java',
      'internal/storage/file/WindowCacheStatAccessor.java',
      'lib/ObjectIdSerialization.java',
    ]
  ],
  deps = [
    '//lib:gson',
    '//lib/jgit/org.eclipse.jgit:jgit',
  ],
  visibility = ['//visibility:public'],
)

java_test(
  name = 'jgit_patch_tests',
  test_class = 'org.eclipse.jgit.diff.EditDeserializerTest',
  srcs = glob(['src/test/java/**/*.java']),
  deps = [
    ':server',
    '//lib/jgit/org.eclipse.jgit:jgit',
    '//lib:junit',
  ],
  visibility = ['//visibility:public'],
)
