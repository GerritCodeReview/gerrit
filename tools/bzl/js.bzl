def _npm_binary_impl(ctx):
  name = ctx.name
  version=ctx.attr.version
  sha1 = ctx.attr.sha1
  repository = ctx.attr.repository
  if not repository:
      repository = "NPMJS"

  dir = '%s-%s' % (name, version)
  filename = '%s.tgz' % dir
  base =  '%s@%s.npm_binary.tgz' % (name, version)
  dest = ctx.path(base)
  if repository == "GERRIT":
    url = 'http://gerrit-maven.storage.googleapis.com/npm-packages/%s' % filename
  elif repository == "NPMJS":
    url = 'http://registry.npmjs.org/%s/-/%s' % (name, filename)
  else:
    fail('invalid repository: %s' % repository)

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
      # huh? Label resolves within the main repo.
      "_download_script": attr.label(default=Label("//tools:download_file.py")),
      "version": attr.string(default=""),
      "sha1": attr.string(default=""),
      "repository": attr.string(default=""),
    })
