java_library(
  name = 'bcprov',
  neverlink = 1,
  exports = ['@bcprov//jar'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'bcpg',
  exports = ['@bcpg//jar'],
  runtime_deps = [':bcprov'],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'bcpkix',
  exports = ['@bcpkix//jar'],
  runtime_deps = [':bcprov'],
  visibility = ['//visibility:public'],
)
