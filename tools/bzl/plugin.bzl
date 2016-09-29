
def gerrit_plugin(
    name,
    deps = [],
    srcs = [],
    resources = [],
    manifest_entries = [],
    **kwargs):
  # TODO(davido): Fix stamping: run git describe in plugin directory
  # https://github.com/bazelbuild/bazel/issues/1758
  manifest_lines = [
    "Gerrit-ApiType: plugin",
    "Implementation-Version: 1.0",
    "Implementation-Vendor: Gerrit Code Review",
  ]
  for line in manifest_entries:
    manifest_lines.append(line.replace('$', '\$'))

  native.java_library(
    name = name + '__plugin',
    srcs = srcs,
    resources = resources,
    deps = deps + ['//gerrit-plugin-api:lib-neverlink'],
    visibility = ['//visibility:public'],
  )

  native.java_binary(
    name = name,
    deploy_manifest_lines = manifest_lines,
    main_class = 'Dummy',
    runtime_deps = [
      ':%s__plugin' % name,
    ],
    visibility = ['//visibility:public'],
    **kwargs
  )
