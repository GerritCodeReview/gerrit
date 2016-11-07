exports_files(glob([
  "LICENSE-*"
]))

filegroup(
  name = 'all-licenses',
  srcs = glob(['LICENSE-*'], exclude = ['LICENSE-DO_NOT_DISTRIBUTE']),
  visibility = ['//visibility:public'],
)

java_library(
  name = 'servlet-api-3_1',
  neverlink = 1,
  exports = ['@servlet_api_3_1//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'servlet-api-3_1-without-neverlink',
  exports = ['@servlet_api_3_1//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'gwtjsonrpc',
  exports = ['@gwtjsonrpc//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'gwtjsonrpc_src',
  exports = ['@gwtjsonrpc//src'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'gson',
  exports = ['@gson//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'gwtorm_client',
  exports = ['@gwtorm_client//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'gwtorm_client_src',
  exports = ['@gwtorm_client//src'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'protobuf',
  exports = ['@protobuf//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-protobuf'],
)

java_library(
  name = 'gwtorm',
  exports = [':gwtorm_client'],
  runtime_deps = [':protobuf'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'guava',
  exports = ['@guava//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'velocity',
  exports = ['@velocity//jar'],
  runtime_deps = [
    '//lib/commons:collections',
    '//lib/commons:lang',
    '//lib/commons:oro',
  ],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'jsch',
  exports = ['@jsch//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-jsch'],
)

java_library(
  name = 'juniversalchardet',
  exports = ['@juniversalchardet//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-MPL1.1'],
)

java_library(
  name = 'args4j',
  exports = ['@args4j//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-args4j'],
)

java_library(
  name = 'automaton',
  exports = ['@automaton//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-automaton'],
)

java_library(
  name = 'pegdown',
  exports = ['@pegdown//jar'],
  runtime_deps = [':grappa'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'grappa',
  exports = ['@grappa//jar'],
  runtime_deps = [
    ':jitescript',
    '//lib/ow2:ow2-asm',
    '//lib/ow2:ow2-asm-analysis',
    '//lib/ow2:ow2-asm-tree',
    '//lib/ow2:ow2-asm-util',
  ],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'jitescript',
  exports = ['@jitescript//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'tukaani-xz',
  exports = ['@tukaani_xz//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-xz'],
)

java_library(
  name = 'mime-util',
  exports = ['@mime_util//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'guava-retrying',
  exports = ['@guava_retrying//jar'],
  runtime_deps = [':jsr305'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'jsr305',
  exports = ['@jsr305//jar'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'blame-cache',
  exports = ['@blame_cache//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)


java_library(
  name = 'h2',
  exports = ['@h2//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-h2'],
)


java_library(
  name = 'jimfs',
  exports = ['@jimfs//jar'],
  runtime_deps = [':guava'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-DO_NOT_DISTRIBUTE'],
)

java_library(
  name = 'junit',
  exports = [
    '@junit//jar',
    ':hamcrest-core',
  ],
  runtime_deps = [':hamcrest-core'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-DO_NOT_DISTRIBUTE'],
)

java_library(
  name = 'hamcrest-core',
  exports = ['@hamcrest_core//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-DO_NOT_DISTRIBUTE'],
)

java_library(
  name = 'truth',
  exports = [
    '@truth//jar',
    ':guava',
    ':junit',
  ],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-DO_NOT_DISTRIBUTE'],
)

java_library(
  name = 'javassist',
  exports = ['@javassist//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-DO_NOT_DISTRIBUTE'],
)

java_library(
  name = 'derby',
  exports = ['@derby//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'soy',
  exports = ['@soy//jar'],
  runtime_deps = [
    ':args4j',
    ':guava',
    ':gson',
    ':icu4j',
    ':jsr305',
    ':protobuf',
    '//lib/guice:guice',
    '//lib/guice:guice-assistedinject',
    '//lib/guice:multibindings',
    '//lib/guice:javax-inject',
    '//lib/ow2:ow2-asm',
    '//lib/ow2:ow2-asm-analysis',
    '//lib/ow2:ow2-asm-commons',
    '//lib/ow2:ow2-asm-util',
  ],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'icu4j',
  exports = [ '@icu4j//jar' ],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-icu4j'],
)

java_library(
  name = 'postgresql',
  exports = ['@postgresql//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-postgresql'],
)

java_library(
  name = 'commons-io',
  exports = ['@commons_io//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)
