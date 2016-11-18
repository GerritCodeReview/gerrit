[java_library(
  name = n,
  exports = ['@%s//jar' % n.replace("-", "_")],
  visibility = ["//visibility:public"],
  data = ['//lib:LICENSE-Apache2.0'],
) for n in [
  'ant',
  'colt',
  'dev',
  'javax-validation',
  'jsinterop-annotations',
  'tapestry',
  'user',
  'w3c-css-sac',
]]

java_library(
  name = 'user-neverlink',
  exports = ['@user//jar'],
  visibility = ['//visibility:public'],
  neverlink = 1,
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'dev-neverlink',
  exports = ['@dev//jar'],
  visibility = ['//visibility:public'],
  neverlink = 1,
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'javax-validation_src',
  exports = ['@javax_validation//src'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

java_library(
  name = 'jsinterop-annotations_src',
  exports = ['@jsinterop_annotations//src'],
  visibility = ['//visibility:public'],
  data = ['//lib:LICENSE-Apache2.0'],
)

