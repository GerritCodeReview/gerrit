
def _npm_binary_impl(repository_ctx):
  out = repository_ctx.execute(["ls"])
  print(out)
  ctx = repository_ctx

  name= ctx.attr.name
  version=ctx.attr.version
  sha1 = ctx.attr.sha1
  repository = ctx.attr.repository
  if not repository:
      repository = "NPMJS"

  dir = '%s-%s' % (name, version)
  filename = '%s.tgz' % dir
  dest = '%s@%s.npm_binary.tgz' % (name, version)
  if repository == "GERRIT":
    url = 'http://gerrit-maven.storage.googleapis.com/npm-packages/%s' % filename
  elif repository == "NPMJS":
    url = 'http://registry.npmjs.org/%s/-/%s' % (name, filename)
  else:
    fail('invalid repository: %s' % repository)

  ctx.execute(["python", "tools/download_file.py", "-o", dest, "-u", url])

npm_binary = repository_rule(
    implementation=_npm_binary_impl,
    local=False,
    attrs= {
        "name": attr.string(),
        "version": attr.string(),
        "sha1": attr.string(),
#        "repository": attr.string(),
    })
