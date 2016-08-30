java_library(
  name = 'guice',
  exports = [
    ':guice_library',
    ':javax-inject',
  ],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'guice_library',
  exports = ['@guice_library//jar'],
  runtime_deps = ['aopalliance'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'guice-assistedinject',
  exports = ['@guice_assistedinject//jar'],
  runtime_deps = [':guice'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'guice-servlet',
  exports = ['@guice_servlet//jar'],
  runtime_deps = [':guice'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'aopalliance',
  exports = ['@aopalliance//jar'],
)

java_library(
  name = 'javax-inject',
  exports = ['@javax_inject//jar'],
)
