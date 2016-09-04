[java_library(
  name = n,
  exports = ['@%s//jar' % n.replace("-", "_")],
  visibility = ["//visibility:public"],
) for n in [
  'javax-validation',
  'dev',
  'user',
]]

java_library(
  name = 'javax-validation_src',
  exports = ['@javax_validation_src//jar'],
  visibility = ['//visibility:public'],
)
