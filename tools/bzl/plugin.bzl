load('//tools/bzl:genrule2.bzl', 'genrule2')

def gerrit_plugin(
    name,
    deps = [],
    srcs = [],
    resources = [],
    manifest_entries = [],
    **kwargs):
  manifest_lines = [
    "Gerrit-ApiType: plugin",
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
    name = '%s__non_stamped' % name,
    deploy_manifest_lines = manifest_lines,
    main_class = 'Dummy',
    runtime_deps = [
      ':%s__plugin' % name,
    ],
    visibility = ['//visibility:public'],
    **kwargs
  )

  # TODO(davido): Remove manual merge of manifest file when this feature
  # request is implemented: https://github.com/bazelbuild/bazel/issues/2009
  genrule2(
    name = name,
    stamp = 1,
    srcs = ['%s__non_stamped_deploy.jar' % name],
    cmd = " && ".join([
      "GEN_VERSION=$$(cat bazel-out/stable-status.txt | grep %s | cut -d ' ' -f 2)" % name.upper(),
      "cd $$TMP",
      "unzip -q $$ROOT/$<",
      "echo \"Implementation-Version: $$GEN_VERSION\n$$(cat META-INF/MANIFEST.MF)\" > META-INF/MANIFEST.MF",
      "zip -qr $$ROOT/$@ ."]),
    outs = ['%s.jar' % name],
    visibility = ['//visibility:public'],
  )
