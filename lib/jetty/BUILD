java_library(
  name = 'servlet',
  exports = ['@jetty_servlet//jar'],
  runtime_deps = [':security'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'security',
  exports = ['@jetty_security//jar'],
  runtime_deps = [':server'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'servlets',
  exports = ['@jetty_servlets//jar'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'server',
  exports = [
    '@jetty_server//jar',
    ':continuation',
    ':http',
  ],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'jmx',
  exports = [
    '@jetty_jmx//jar',
    ':continuation',
    ':http',
  ],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'continuation',
  exports = ['@jetty_continuation//jar'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'http',
  exports = [
    '@jetty_http//jar',
    ':io',
  ],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'io',
  exports = [
    '@jetty_io//jar',
    ':util',
  ],
)

java_library(
  name = 'util',
  exports = ['@jetty_util//jar'],
)
