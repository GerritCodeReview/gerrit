java_library(
  name = 'fluent-hc',
  exports = ['@fluent_hc//jar'],
  runtime_deps = [':httpclient'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'httpclient',
  exports = ['@httpclient//jar'],
  runtime_deps = [
    '//lib/commons:codec',
    ':httpcore',
    '//lib/log:jcl-over-slf4j',
  ],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'httpcore',
  exports = ['@httpcore//jar'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'httpmime',
  exports = ['@httpmime//jar'],
  visibility = ['//visibility:public'],
)
