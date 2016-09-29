def documentation_attributes(revision):
  return [
    "toc",
    'newline="\\n"',
    'asterisk="&#42;"',
    'plus="&#43;"',
    'caret="&#94;"',
    'startsb="&#91;"',
    'endsb="&#93;"',
    'tilde="&#126;"',
    "last-update-label!",
    "source-highlighter=prettify",
    "stylesheet=DEFAULT",
    "linkcss=true",
    "prettifydir=.",
    'revnumber="%s"' % revision,
  ]

def genasciidoc_htmlonly(
    name,
    srcs = [],
    attributes = [],
    backend = None,
    searchbox = True,
    **kwargs):
  EXPN = "." + name + "_expn"

  asciidoc = [
    "$(location //lib/asciidoctor:asciidoc)",
    "--bazel",
    "--in-ext", ".txt" + EXPN,
    "--out-ext", '".html"',
    "--tmp", "$(@D)",
  ]
  if backend:
    asciidoc.extend(["-b", backend])
  for attribute in attributes:
    asciidoc.extend(["-a", attribute])
  asciidoc.append("$(SRCS)")
  newsrcs = []
  outs = [ "asciidoctor.css" ]
  for src in srcs:
    tools = [ "//Documentation:replace_macros.py" ]
    fn = src
    # We have two cases: regular source files and generated files.
    # Generated files are passed as targets ":foo", and ":" is removed.
    # 1. regular files: cmd = "-s $(SRCS)", srcs = ["foo"]
    # 2. generated files: cmd = "-s $(location :foo)", srcs = []
    srcs = [src]
    passed_src = "$(SRCS)"
    if fn.startswith(":") :
      fn = src[1:]
      srcs = []
      passed_src = "$(location :%s)" % fn
      tools.append(":%s" % fn)
    ex = fn + EXPN

    native.genrule(
      name = ex + "_rule",
      cmd = "python $(location //Documentation:replace_macros.py)" +
        ' --suffix="%s"' % EXPN +
        " -s " + passed_src + " -o $@" +
        (" --searchbox" if searchbox else " --no-searchbox"),
      srcs = srcs,
      outs = [ ex ],
      tools = tools,
    )

    newsrcs.append(":%s" % ex)
    outs.append(fn.replace(".txt", ".html"))

  native.genrule(
    name = name + "_gen",
    cmd = " ".join(asciidoc),
    srcs = newsrcs,
    tools = [ "//lib/asciidoctor:asciidoc" ],
    outs = outs,
  )

  native.filegroup(
    name = name,
    data = outs,
    **kwargs
  )

def genasciidoc(
    name,
    srcs = [],
    attributes = [],
    backend = None,
    searchbox = True,
    resources = True,
    **kwargs):
  SUFFIX = "_htmlonly"

  genasciidoc_htmlonly(
    name = name + SUFFIX if resources else name,
    srcs = srcs,
    attributes = attributes,
    backend = backend,
    searchbox = searchbox,
    **kwargs
  )

  if resources:
    htmlonly = ":" + name + SUFFIX
    native.filegroup(
      name = name,
      data =[
        htmlonly,
        "//Documentation:resources",
      ],
      **kwargs
    )
