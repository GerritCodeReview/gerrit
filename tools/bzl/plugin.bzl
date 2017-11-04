load("//tools/bzl:genrule2.bzl", "genrule2")
load(
    "//tools/bzl:gwt.bzl",
    "GWT_PLUGIN_DEPS",
    "GWT_PLUGIN_DEPS_NEVERLINK",
    "GWT_TRANSITIVE_DEPS",
    "GWT_COMPILER_ARGS",
    "GWT_JVM_ARGS",
    "gwt_binary",
)
load("//tools/bzl:asciidoc.bzl", "documentation_attributes")
load("//tools/bzl:asciidoc.bzl", "genasciidoc")
load("//tools/bzl:asciidoc.bzl", "genasciidoc_zip")

PLUGIN_DEPS = ["//plugins:plugin-lib"]

PLUGIN_DEPS_NEVERLINK = ["//plugins:plugin-lib-neverlink"]

PLUGIN_TEST_DEPS = [
    "//java/com/google/gerrit/acceptance:lib",
    "//lib/bouncycastle:bcpg",
    "//lib/bouncycastle:bcpkix",
    "//lib/bouncycastle:bcprov",
]

def gerrit_plugin(
    name,
    deps = [],
    provided_deps = [],
    srcs = [],
    gwt_module = [],
    resources = [],
    documentation = [],
    doc_prefix = "Documentation",
    manifest_entries = [],
    target_suffix = "",
    **kwargs):
  docs = []
  docs2 = ''
  if documentation:
    native.genrule(
        name = "index",
        srcs = documentation,
        outs = ["index__plugin.jar"],
        cmd = "$(location //lib/asciidoctor:doc_indexer) " +
              "-o $(OUTS) " +
              "--prefix \"%s/\" " % doc_prefix +
              "--in-ext \".txt\" " +
              "--out-ext \".html\" " +
              "$(SRCS)",
        tools = ["//lib/asciidoctor:doc_indexer"],
        visibility = ['//visibility:public'],
    )
    genasciidoc(
        name = "Documentation",
        srcs = documentation,
        attributes = documentation_attributes(),
        backend = "html5",
        visibility = ["//visibility:public"],
    )
    genasciidoc_zip(
        name = "html",
        srcs = documentation,
        attributes = documentation_attributes(),
        backend = "html5",
        directory = "Documentation",
        visibility = ["//visibility:public"],
    )
    genasciidoc_zip(
        name = "searchfree",
        srcs = documentation,
        attributes = documentation_attributes(),
        backend = "html5",
        directory = "Documentation",
        searchbox = False,
        visibility = ["//visibility:public"],
    )
    docs = [':index']
    docs2 = ':html'

  native.java_library(
    name = name + '__plugin',
    srcs = srcs,
    resources = resources + docs,
    deps = provided_deps + deps + GWT_PLUGIN_DEPS_NEVERLINK + PLUGIN_DEPS_NEVERLINK,
    visibility = ['//visibility:public'],
    **kwargs
  )

  static_jars = []
  if gwt_module:
    static_jars = [':%s-static' % name]

  native.java_binary(
    name = '%s__non_stamped' % name,
    deploy_manifest_lines = manifest_entries + [
      "Gerrit-ApiType: plugin",
      "Implementation-Vendor: Gerrit Code Review",
    ],
    main_class = 'Dummy',
    runtime_deps = [
      ':%s__plugin' % name,
    ] + static_jars,
    visibility = ['//visibility:public'],
    **kwargs
  )

  if gwt_module:
    native.java_library(
      name = name + '__gwt_module',
      resources = depset(srcs + resources).to_list(),
      runtime_deps = deps + GWT_PLUGIN_DEPS,
      visibility = ['//visibility:public'],
      **kwargs
    )
    genrule2(
      name = '%s-static' % name,
      cmd = ' && '.join([
        'mkdir -p $$TMP/static',
        'unzip -qd $$TMP/static $(location %s__gwt_application)' % name,
        'cd $$TMP',
        'zip -qr $$ROOT/$@ .']),
      tools = [':%s__gwt_application' % name],
      outs = ['%s-static.jar' % name],
    )
    gwt_binary(
      name = name + '__gwt_application',
      module = [gwt_module],
      deps = GWT_PLUGIN_DEPS + GWT_TRANSITIVE_DEPS + ['//lib/gwt:dev'],
      module_deps = [':%s__gwt_module' % name],
      compiler_args = GWT_COMPILER_ARGS,
      jvm_args = GWT_JVM_ARGS,
    )

  # TODO(davido): Remove manual merge of manifest file when this feature
  # request is implemented: https://github.com/bazelbuild/bazel/issues/2009
  genrule2(
    name = name + target_suffix,
    stamp = 1,
    srcs = ['%s__non_stamped_deploy.jar' % name],
    cmd = " && ".join([
      "GEN_VERSION=$$(cat bazel-out/stable-status.txt | grep -w STABLE_BUILD_%s_LABEL | cut -d ' ' -f 2)" % name.upper(),
      "cd $$TMP",
      "unzip -q $$ROOT/$<",
      "echo \"Implementation-Version: $$GEN_VERSION\n$$(cat META-INF/MANIFEST.MF)\" > META-INF/MANIFEST.MF",
      "zip -qr $$ROOT/$@ ."
    ]),
    outs = ['%s%s.jar' % (name, target_suffix)],
    visibility = ['//visibility:public'],
  )
