java_library(
  name = 'powermock-module-junit4',
  exports = [
    '@powermock_module_junit4//jar',
    ':powermock-module-junit4-common',
    '//lib:junit',
  ],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'powermock-module-junit4-common',
  exports = [
    '@powermock_module_junit4_common//jar',
    ':powermock-reflect',
    '//lib:junit',
  ],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'powermock-reflect',
  exports = [
    '@powermock_reflect//jar',
    '//lib:junit',
    '//lib/easymock:objenesis',
  ],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'powermock-api-easymock',
  exports = [
    '@powermock_api_easymock//jar',
    ':powermock-api-support',
    '//lib/easymock:easymock',
  ],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'powermock-api-support',
  exports = [
    '@powermock_api_support//jar',
    ':powermock-core',
    ':powermock-reflect',
    '//lib:junit',
  ],
  visibility = ['//visibility:public'],
)

java_library(
  name = 'powermock-core',
  exports = [
    ':powermock-reflect',
    '//lib:javassist',
    '//lib:junit',
  ],
  visibility = ['//visibility:public'],
)
