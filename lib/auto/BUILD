java_plugin(
  name = 'auto_plugin',
  processor_class = 'com.google.auto.value.processor.AutoValueProcessor',
  deps = ['@auto_value//jar'],
)

java_library(
  name = 'auto-value',
  exported_plugins = [':auto_plugin'],
  exports = ['@auto_value//jar'],
  visibility = ['//visibility:public'],
)
