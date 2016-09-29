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
    '--suffix', ctx.attr.expand_extension,
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
    progress_message = "Replacing macros in %s" % ctx.file.src.short_path,
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
    "expand_extension": attr.string(mandatory = True),
    "searchbox": attr.bool(default = True),
    "out": attr.output(mandatory = True),
  },
)

def _asciidoc_impl(ctx):
  args = [
    "--bazel",
    "--in-ext", ".txt" + ctx.attr.expand_extension,
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
    progress_message = "Rendering asciidoctor files",
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
    "expand_extension": attr.string(mandatory = True),
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
  EXPAND_EXTENSION = "." + name + "_expn"
  new_srcs = []
  outs = [ "asciidoctor.css" ]

  for src in srcs:
    fn = src
    if fn.startswith(":"):
      fn = src[1:]

    replace_macros(
      name = "macros_%s_%s" % (name, fn),
      src = src,
      out = fn + EXPAND_EXTENSION,
      expand_extension = EXPAND_EXTENSION,
      searchbox = searchbox,
    )

    new_srcs.append(":" + fn + EXPAND_EXTENSION)
    outs.append(fn.replace(".txt", ".html"))

  asciidoc(
    name = name + "_gen",
    srcs = new_srcs,
    expand_extension = EXPAND_EXTENSION,
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
      data = [
        htmlonly,
        "//Documentation:resources",
      ],
      **kwargs
    )

def _asciidoc_html_zip_impl(ctx):
  args = [
    "--mktmp",
    "-z", ctx.outputs.out.path,
    "--in-ext", ".txt" + ctx.attr.expand_extension,
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
    outputs = [ ctx.outputs.out ],
    executable = ctx.executable._exe,
    arguments = args,
    progress_message = "Rendering asciidoctor files",
  )

asciidoc_html_zip = rule(
  implementation = _asciidoc_html_zip_impl,
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
    "expand_extension": attr.string(mandatory = True),
    "backend": attr.string(),
    "attributes": attr.string_list(),
  },
  outputs = {
    "out": "%{name}.zip",
  }
)

def genasciidoc_htmlonly_zip(
    name,
    srcs = [],
    attributes = [],
    backend = None,
    searchbox = True,
    **kwargs):
  EXPAND_EXTENSION = "." + name + "_expn"
  new_srcs = []

  for src in srcs:
    fn = src
    if fn.startswith(":"):
      fn = src[1:]

    replace_macros(
      name = "macros_%s_%s" % (name, fn),
      src = src,
      out = fn + EXPAND_EXTENSION,
      expand_extension = EXPAND_EXTENSION,
      searchbox = searchbox,
    )

    new_srcs.append(":" + fn + EXPAND_EXTENSION)

  asciidoc_html_zip(
    name = name,
    srcs = new_srcs,
    expand_extension = EXPAND_EXTENSION,
    backend = backend,
    attributes = attributes,
  )

def _asciidoc_zip_impl(ctx):
  tmpdir = ctx.outputs._tmp.path + ".dir/"
  cmd = [
    "p=$PWD",
    "touch %s" % ctx.outputs._tmp.path,
    "mkdir -p %s" % tmpdir,
    "cd %s" % tmpdir,
    "unzip -q $p/%s -d %s/" % (ctx.file.src.path, ctx.attr.directory),
  ]
  for resource in ctx.files.resources:
    parent_dir = resource.short_path
    index = parent_dir.rfind("/")
    if index != -1:
      parent_dir = parent_dir[:index]
      cmd.append("mkdir -p %s" % parent_dir)
    else:
      parent_dir = "."
    cmd.append("cp $p/%s %s" % (resource.path, parent_dir))
  cmd.append("zip -qr $p/%s *" % ctx.outputs.out.path)
  ctx.action(
    inputs = [ ctx.file.src ] + ctx.files.resources,
    outputs = [ ctx.outputs.out, ctx.outputs._tmp ],
    command = " && ".join(cmd),
    progress_message =
        "Generating asciidoctor zip file %s" % ctx.outputs.out.short_path,
  )

asciidoc_zip = rule(
  implementation = _asciidoc_zip_impl,
  attrs = {
    "src": attr.label(
      mandatory = True,
      allow_single_file = [".zip"],
    ),
    "resources": attr.label_list(mandatory = True, allow_files = True),
    "directory": attr.string(mandatory = True),
  },
  outputs = {
    "out": "%{name}.zip",
    "_tmp": "%{name}_tmp",
  }
)

def genasciidoc_zip(
    name,
    directory,
    srcs = [],
    attributes = [],
    backend = None,
    searchbox = True,
    resources = True,
    **kwargs):
  SUFFIX = "_htmlonly"

  genasciidoc_htmlonly_zip(
    name = name + SUFFIX if resources else name,
    srcs = srcs,
    attributes = attributes,
    backend = backend,
    searchbox = searchbox,
    **kwargs
  )

  if resources:
    htmlonly = ":" + name + SUFFIX
    asciidoc_zip(
      name = name,
      src = htmlonly,
      resources = [ "//Documentation:resources" ],
      directory = directory,
    )
