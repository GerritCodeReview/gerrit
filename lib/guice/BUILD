java_library(
  name = 'guice',
  exports = [
    ':guice_library',
    ':javax-inject',
    ':multibindings',
  ],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'guice_library',
  exports = ['@guice_library//jar'],
  runtime_deps = ['aopalliance'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'guice-assistedinject',
  exports = ['@guice_assistedinject//jar'],
  runtime_deps = [':guice'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'guice-servlet',
  exports = ['@guice_servlet//jar'],
  runtime_deps = [':guice'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'aopalliance',
  exports = ['@aopalliance//jar'],
  data = ['//lib:LICENSE-PublicDomain'],
)

java_library(
  name = 'javax-inject',
  exports = ['@javax_inject//jar'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'multibindings',
  exports = [ '@multibindings//jar' ],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)
