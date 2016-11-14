GERRIT = 'GERRIT:'
GERRIT_API = 'GERRIT_API:'
MAVEN_CENTRAL = 'MAVEN_CENTRAL:'
MAVEN_LOCAL = 'MAVEN_LOCAL:'

def _maven_release(ctx, parts):
  """induce jar and url name from maven coordinates."""
  if len(parts) not in [3, 4]:
    fail('%s:\nexpected id="groupId:artifactId:version[:classifier]"'
         % ctx.attr.artifact)
  if len(parts) == 4:
    group, artifact, version, classifier = parts
    file_version = version + '-' + classifier
  else:
    group, artifact, version = parts
    file_version = version

  jar = artifact.lower() + '-' + file_version
  url = '/'.join([
    ctx.attr.repository,
    group.replace('.', '/'),
    artifact,
    version,
    artifact + '-' + file_version])

  return jar, url

# Creates a struct containing the different parts of an artifact's FQN
def _create_coordinates(fully_qualified_name):
  parts = fully_qualified_name.split(":")
  packaging = None
  classifier = None

  if len(parts) == 3:
    group_id, artifact_id, version = parts
  elif len(parts) == 4:
    group_id, artifact_id, version, packaging = parts
  elif len(parts) == 5:
    group_id, artifact_id, version, packaging, classifier = parts
  else:
    fail("Invalid fully qualified name for artifact: %s" % fully_qualified_name)

  return struct(
      fully_qualified_name = fully_qualified_name,
      group_id = group_id,
      artifact_id = artifact_id,
      packaging = packaging,
      classifier = classifier,
      version = version,
  )

# Provides the syntax "@jar_name//jar" for bin classifier
# and "@jar_name//src" for sources
def _generate_build_file(ctx, classifier, filename):
  contents = """
# DO NOT EDIT: automatically generated BUILD file for maven_archive rule {rule_name}
java_import(
    name = '{classifier}',
    jars = ['{filename}'],
    visibility = ['//visibility:public']
)
filegroup(
    name = 'file',
    srcs = ['{filename}'],
    visibility = ['//visibility:public']
)\n""".format(classifier = classifier,
              rule_name = ctx.name,
              filename = filename)
  ctx.file('%s/BUILD' % ctx.path(classifier), contents, False)

def _maven_jar_impl(ctx):
  """rule to download a Maven archive."""
  coordinates = _create_coordinates(ctx.attr.artifact)

  name = ctx.name
  sha1 = ctx.attr.sha1

  parts = ctx.attr.artifact.split(':')
  # TODO(davido): Only releases for now, implement handling snapshots
  jar, url = _maven_release(ctx, parts)

  binjar = jar + '.jar'
  binjar_path = ctx.path('/'.join(['jar', binjar]))
  binurl = url + '.jar'

  srcjar = jar + '-src.jar'
  srcjar_path = ctx.path('/'.join(['src', srcjar]))
  srcurl = url + '-sources.jar'

  python = ctx.which("python")
  script = ctx.path(ctx.attr._download_script)

  args = [python, script, "-o", binjar_path, "-u", binurl, "-v", sha1]
  if ctx.attr.unsign:
    args.append('--unsign')
  for x in ctx.attr.exclude:
    args.extend(['-x', x])

  out = ctx.execute(args)

  if out.return_code:
    fail("failed %s: %s" % (' '.join(args), out.stderr))
  _generate_build_file(ctx, "jar", binjar)

  if ctx.attr.src_sha1 or ctx.attr.attach_source:
    args = [python, script, "-o", srcjar_path, "-u", srcurl]
    if ctx.attr.src_sha1:
      args.extend(['-v', ctx.attr.src_sha1])
    out = ctx.execute(args)
    if out.return_code:
      fail("failed %s: %s" % (args, out.stderr))
    _generate_build_file(ctx, "src", srcjar)

maven_jar=repository_rule(
  implementation=_maven_jar_impl,
  local=True,
  attrs={
    "artifact": attr.string(mandatory=True),
    "sha1": attr.string(mandatory=True),
    "src_sha1": attr.string(),
    "_download_script": attr.label(default=Label("//tools:download_file.py")),
    "repository": attr.string(default=MAVEN_CENTRAL),
    "attach_source": attr.bool(default=True),
    "unsign": attr.bool(default=False),
    "exclude": attr.string_list(),
  })
