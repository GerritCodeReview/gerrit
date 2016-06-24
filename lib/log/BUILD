java_library(
  name = 'api',
  exports = ['@log_api//jar'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'nop',
  exports = ['@log_nop//jar'],
  runtime_deps = [':api'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'impl_log4j',
  exports = ['@impl_log4j//jar'],
  runtime_deps = [':log4j'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'jcl-over-slf4j',
  exports = ['@jcl_over_slf4j//jar'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'log4j',
  exports = ['@log4j//jar'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'jsonevent-layout',
  exports = ['@jsonevent_layout//jar'],
  runtime_deps = [
    ':json-smart',
    '//lib/commons:lang'
  ],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'json-smart',
  exports = ['@json_smart//jar'],
  visibility = ['//visibility:public'],
)
