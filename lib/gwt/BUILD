[java_library(
  name = n,
  exports = ['@%s//jar' % n.replace("-", "_")],
  visibility = ["//visibility:public"],
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
  name = 'javax-validation_src',
  exports = ['@javax_validation_src//jar'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'jsinterop-annotations_src',
  exports = ['@jsinterop_annotations_src//jar'],
  visibility = ['//visibility:public'],
)

