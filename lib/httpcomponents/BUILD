package(default_visibility = ['//visibility:public'])

java_library(
  name = 'fluent-hc',
  exports = ['@fluent_hc//jar'],
  runtime_deps = [':httpclient'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
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
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'httpcore',
  exports = ['@httpcore//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'httpmime',
  exports = ['@httpmime//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'httpasyncclient',
  exports = [ '@httpasyncclient//jar' ],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'httpcore-nio',
  exports =  [ '@httpcore_nio//jar' ],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'httpcore-niossl',
  exports = ['@httpcore_niossl//jar'],
  data = ['//lib:LICENSE-Apache2.0'],
)
