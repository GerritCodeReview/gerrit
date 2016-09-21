
def _npm_binary_impl(ctx):
  name = ctx.attr.name
  version=ctx.attr.version
  sha1 = ctx.attr.sha1
  repository = ctx.attr.repository
  if not repository:
      repository = "NPMJS"

  print("name:"+name)

  dir = '%s-%s' % (name, version)
  filename = '%s.tgz' % dir
  dest = '%s@%s.npm_binary.tgz' % (name, version)
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
  ctx.file("BUILD", "", executable=False)

npm_binary = repository_rule(
    implementation=_npm_binary_impl,
    local=True,
    attrs= {
      # huh? Label resolves within the main repo.
      "_download_script": attr.label(default=Label("//tools:download_file.py")),
      "name": attr.string(default="", mandatory=True),
      "version": attr.string(default="", mandatory=True),
      "sha1": attr.string(default="", mandatory=True),
      "repository": attr.string(default=""),
    })
