java_library(
  name = 'consumer',
  exports = ['@openid_consumer//jar'],
  runtime_deps = [
    ':nekohtml',
    ':xerces',
    '//lib/httpcomponents:httpclient',
    '//lib/log:jcl-over-slf4j',
    '//lib/guice:guice',
  ],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'nekohtml',
  exports = ['@nekohtml//jar'],
  runtime_deps = [':xerces'],
)

java_library(
  name = 'xerces',
  exports = ['@xerces//jar'],
)
