def documentation_attributes():
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
  ]

def _replace_macros_impl(ctx):
  cmd = [
    ctx.file._exe.path,
    '--suffix', ctx.attr.expn,
    "-s", ctx.file.src.path,
    "-o", ctx.outputs.out.path,
  ]
  if ctx.attr.searchbox:
    cmd.append('--searchbox')
  else:
    cmd.append('--no-searchbox')
  ctx.action(
    inputs = [ctx.file._exe, ctx.file.src],
    outputs = [ctx.outputs.out],
    command = cmd,
  )

replace_macros = rule(
  implementation = _replace_macros_impl,
  attrs = {
    "_exe": attr.label(
      default = Label("//Documentation:replace_macros.py"),
      allow_single_file = True,
    ),
    "src": attr.label(
      mandatory = True,
      allow_single_file = [".txt"],
    ),
    "expn": attr.string(mandatory = True),
    "searchbox": attr.bool(default = True),
    "out": attr.output(mandatory = True),
  },
)

def _asciidoc_impl(ctx):
  args = [
    "--bazel",
    "--in-ext", ".txt" + ctx.attr.expn,
    "--out-ext", ".html",
  ]
  if ctx.attr.backend:
    args.extend(["-b", ctx.attr.backend])
  for attribute in ctx.attr.attributes:
    args.extend(["-a", attribute])
  args.extend([
    "--revnumber-file", ctx.file.version.path,
  ])
  for src in ctx.files.srcs:
    args.append(src.path)
  ctx.action(
    inputs = ctx.files.srcs + [ ctx.executable._exe, ctx.file.version ],
    outputs = ctx.outputs.outs,
    executable = ctx.executable._exe,
    arguments = args,
  )

asciidoc = rule(
  implementation = _asciidoc_impl,
  attrs = {
    "_exe": attr.label(
      default = Label("//lib/asciidoctor:asciidoc"),
      allow_files = True,
      executable = True,
    ),
    "srcs": attr.label_list(mandatory = True, allow_files = True),
    "version": attr.label(
      default = Label("//:version.txt"),
      allow_single_file = True,
    ),
    "expn": attr.string(mandatory = True),
    "backend": attr.string(),
    "attributes": attr.string_list(),
    "outs": attr.output_list(mandatory = True),
  },
)

def genasciidoc_htmlonly(
    name,
    srcs = [],
    attributes = [],
    backend = None,
    searchbox = True,
    **kwargs):
  EXPN = "." + name + "_expn"
  newsrcs = []
  outs = [ "asciidoctor.css" ]

  for src in srcs:
    fn = src
    if fn.startswith(":"):
      fn = src[1:]

    replace_macros(
      name = "macros_%s_%s" % (name, fn),
      src = src,
      out = fn + EXPN,
      expn = EXPN,
      searchbox = searchbox,
    )

    newsrcs.append(":" + fn + EXPN)
    outs.append(fn.replace(".txt", ".html"))

  asciidoc(
    name = name + "_gen",
    srcs = newsrcs,
    expn = EXPN,
    backend = backend,
    attributes = attributes,
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
