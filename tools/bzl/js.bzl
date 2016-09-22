NPMJS = "NPMJS"
GERRIT = "GERRIT"

NPM_VERSIONS = {"bower":   '1.7.9'}
NPM_SHA1S = {"bower":  'b7296c2393e0d75edaa6ca39648132dd255812b0'}

def _npm_tarball(name):
  return "%s@%s.npm_binary.tgz" % (name, NPM_VERSIONS[name])

def _npm_binary_impl(ctx):
  name = ctx.name
  version= NPM_VERSIONS[name]
  sha1 = NPM_VERSIONS[name]

  dir = '%s-%s' % (name, version)
  filename = '%s.tgz' % dir
  base = '%s@%s.npm_binary.tgz' % (name, version)
  dest = ctx.path(base)
  repository = ctx.attr.repository
  if repository == GERRIT:
    url = 'http://gerrit-maven.storage.googleapis.com/npm-packages/%s' % filename
  elif repository == NPMJS:
    url = 'http://registry.npmjs.org/%s/-/%s' % (name, filename)
  else:
    fail('repository %s not in {%s,%s}' % (repository, GERRIT, NPMJS))

  python = ctx.which("python")
  script = ctx.path(ctx.attr._download_script)

  args = [python, script, "-o", dest, "-u", url]
  out = ctx.execute(args)
  if out.return_code:
    fail("failed %s: %s" % (args, out.stderr))
  ctx.file("BUILD", "filegroup(name='tarball', srcs=['%s'])" % base, False)

npm_binary = repository_rule(
    implementation=_npm_binary_impl,
    local=True,
    attrs= {
      # Label resolves within repo of the .bzl file.
      "_download_script": attr.label(default=Label("//tools:download_file.py")),
      "repository": attr.string(default=NPMJS),
    })

def _run_npm_binary_str(ctx, tarball, name):
  python_bin = ctx.which("python")
  return " ".join([
    python_bin,
    ctx.path(ctx.attr._run_npm),
    ctx.path(tarball)])

def _bower_tarball(ctx):
  download_name = '%s__download_bower' % ctx.name
  cmd = [
      ctx.which("python"),
      ctx.path(ctx.attr._download_bower),
      '-b', '"%s"' % _run_npm_binary_str(ctx, ctx.attr._bower_tarball, "bower"),
      '-n', ctx.name,
      '-p', ctx.attr.package,
      '-v', ctx.attr.version,
      '-s', ctx.attr.sha1,
      '-o', download_name + ".zip",
    ]

  print("running %s" % " ".join(cmd))
  out = ctx.execute(cmd)
  if out.return_code:
    fail("failed %s: %s" % (" ".join(cmd), out.stderr))
  ctx.file("BUILD", "", False)

bower_tarball=repository_rule(
  _bower_tarball,
  attrs = {
    "_bower_tarball": attr.label(default=Label("@bower//:%s" % _npm_tarball("bower"))),
    "_run_npm": attr.label(default=Label("//tools/js:run_npm_binary.py")),
    "_npm_tarball": attr.label(default=Label("//tools/js:run_npm_binary.py")),
    "_download_bower": attr.label(default=Label("//tools/js:download_bower.py")),
    "sha1": attr.string(mandatory=True),
    "version": attr.string(mandatory=True),
    "package": attr.string(mandatory=True),
  })
