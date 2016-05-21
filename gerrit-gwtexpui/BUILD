load('//tools/bzl:gwt.bzl', 'gwt_module')
load('//tools/bzl:junit.bzl', 'junit_tests')

SRC = 'src/main/java/com/google/gwtexpui/'

gwt_module(
  name = 'Clippy',
  srcs = glob([SRC + 'clippy/client/*.java']),
  gwt_xml = SRC + 'clippy/Clippy.gwt.xml',
  resources = [
    SRC + 'clippy/client/clippy.css',
    SRC + 'clippy/client/clippy.swf',
    SRC + 'clippy/client/page_white_copy.png',
    SRC + 'clippy/client/CopyableLabelText.properties',
  ],
  deps = [
    ':SafeHtml',
    ':UserAgent',
    '//lib/gwt:user',
  ],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'CSS',
  srcs = glob([SRC + 'css/rebind/*.java']),
  resources = [SRC + 'css/CSS.gwt.xml'],
  deps = ['//lib/gwt:dev'],
  visibility = ['//visibility:public'],
)

gwt_module(
  name = 'GlobalKey',
  srcs = glob([SRC + 'globalkey/client/*.java']),
  gwt_xml = SRC + 'globalkey/GlobalKey.gwt.xml',
  resources = [
    SRC + 'globalkey/client/KeyConstants.properties',
    SRC + 'globalkey/client/key.css',
  ],
  deps = [
    ':SafeHtml',
    ':UserAgent',
    '//lib/gwt:user',
  ],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'linker_server',
  srcs = glob([SRC + 'linker/server/*.java']),
  deps = ['//lib:servlet-api-3_1'],
  visibility = ['//visibility:public'],
)

gwt_module(
  name = 'Progress',
  srcs = glob([SRC + 'progress/client/*.java']),
  gwt_xml = SRC + 'progress/Progress.gwt.xml',
  resources = [SRC + 'progress/client/progress.css'],
  deps = ['//lib/gwt:user'],
  visibility = ['//visibility:public'],
)

gwt_module(
  name = 'SafeHtml',
  srcs = glob([SRC + 'safehtml/client/*.java']),
  gwt_xml = SRC + 'safehtml/SafeHtml.gwt.xml',
  resources = [SRC + 'safehtml/client/safehtml.css'],
  deps = ['//lib/gwt:user'],
  visibility = ['//visibility:public'],
)

junit_tests(
  name = 'SafeHtml_tests',
  srcs = glob([
    'src/test/java/com/google/gwtexpui/safehtml/client/**/*.java',
  ]),
  deps = [
    ':SafeHtml',
    '//lib:truth',
    '//lib/gwt:user',
    '//lib/gwt:dev',
  ],
)

gwt_module(
  name = 'UserAgent',
  srcs = glob([SRC + 'user/client/*.java']),
  gwt_xml = SRC + 'user/User.gwt.xml',
  resources = [SRC + 'user/client/tooltip.css'],
  deps = ['//lib/gwt:user'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'server',
  srcs = glob([SRC + 'server/*.java']),
  deps = ['//lib:servlet-api-3_1'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'client-src-lib',
  srcs = [],
  resources = glob(
    [SRC + n for n in [
      'clippy/**/*',
      'globalkey/**/*',
      'safehtml/**/*',
      'user/**/*',
    ]]
  ),
  visibility = ['//visibility:public'],
)
