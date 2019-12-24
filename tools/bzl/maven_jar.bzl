GERRIT = "GERRIT:"

GERRIT_API = "GERRIT_API:"

MAVEN_CENTRAL = "MAVEN_CENTRAL:"

MAVEN_LOCAL = "MAVEN_LOCAL:"

ECLIPSE = "ECLIPSE:"

MAVEN_SNAPSHOT = "https://oss.sonatype.org/content/repositories/snapshots"

SNAPSHOT = "-SNAPSHOT-"

def _maven_release(ctx, parts):
    """induce jar and url name from maven coordinates."""
    if len(parts) not in [3, 4]:
        fail('%s:\nexpected id="groupId:artifactId:version[:classifier]"' %
             ctx.attr.artifact)
    if len(parts) == 4:
        group, artifact, version, classifier = parts
        file_version = version + "-" + classifier
    else:
        group, artifact, version = parts
        file_version = version

    repository = ctx.attr.repository

    if "-SNAPSHOT-" in version:
        start = version.index(SNAPSHOT)
        end = start + len(SNAPSHOT) - 1

        # file version without snapshot constant, but with post snapshot suffix
        file_version = version[:start] + version[end:]

        # version without post snapshot suffix
        version = version[:end]

        # overwrite the repository with Maven snapshot repository
        repository = MAVEN_SNAPSHOT

    jar = artifact.lower() + "-" + file_version

    url = "/".join([
        repository,
        group.replace(".", "/"),
        artifact,
        version,
        artifact + "-" + file_version,
    ])

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

def _format_deps(attr, deps):
    formatted_deps = ""
    if deps:
        if len(deps) == 1:
            formatted_deps += "%s = [\'%s\']," % (attr, deps[0])
        else:
            formatted_deps += "%s = [\n" % attr
            for dep in deps:
                formatted_deps += "        \'%s\',\n" % dep
            formatted_deps += "    ],"
    return formatted_deps

def _generate_build_files(ctx, binjar, srcjar):
    header = "# DO NOT EDIT: automatically generated BUILD file for maven_jar rule %s" % ctx.name
    srcjar_attr = ""
    if srcjar:
        srcjar_attr = 'srcjar = "%s",' % srcjar
    contents = """
{header}
load("@rules_java//java:defs.bzl", "java_import")
package(default_visibility = ['//visibility:public'])
java_import(
    name = 'jar',
    jars = ['{binjar}'],
    {srcjar_attr}
    {deps}
    {exports}
)
java_import(
    name = 'neverlink',
    jars = ['{binjar}'],
    neverlink = 1,
    {deps}
    {exports}
)
\n""".format(
        srcjar_attr = srcjar_attr,
        header = header,
        binjar = binjar,
        deps = _format_deps("deps", ctx.attr.deps),
        exports = _format_deps("exports", ctx.attr.exports),
    )
    if srcjar:
        contents += """
java_import(
    name = 'src',
    jars = ['{srcjar}'],
)
""".format(srcjar = srcjar)
    ctx.file("%s/BUILD" % ctx.path("jar"), contents, False)

    # Compatibility layer for java_import_external from rules_closure
    contents = """
{header}
package(default_visibility = ['//visibility:public'])

alias(
    name = "{rule_name}",
    actual = "@{rule_name}//jar",
)
\n""".format(rule_name = ctx.name, header = header)
    ctx.file("BUILD", contents, False)

def _maven_jar_impl(ctx):
    """rule to download a Maven archive."""
    coordinates = _create_coordinates(ctx.attr.artifact)

    name = ctx.name
    sha1 = ctx.attr.sha1

    parts = ctx.attr.artifact.split(":")

    # TODO(davido): Only releases for now, implement handling snapshots
    jar, url = _maven_release(ctx, parts)

    binjar = jar + ".jar"
    binjar_path = ctx.path("/".join(["jar", binjar]))
    binurl = url + ".jar"

    python = ctx.which("python")
    script = ctx.path(ctx.attr._download_script)

    args = [python, script, "-o", binjar_path, "-u", binurl]
    if ctx.attr.sha1:
        args.extend(["-v", sha1])
    for x in ctx.attr.exclude:
        args.extend(["-x", x])

    out = ctx.execute(args)

    if out.return_code:
        fail("failed %s: %s" % (args, out.stderr))

    srcjar = None
    if ctx.attr.src_sha1 or ctx.attr.attach_source:
        srcjar = jar + "-src.jar"
        srcurl = url + "-sources.jar"
        srcjar_path = ctx.path("jar/" + srcjar)
        args = [python, script, "-o", srcjar_path, "-u", srcurl]
        if ctx.attr.src_sha1:
            args.extend(["-v", ctx.attr.src_sha1])
        out = ctx.execute(args)
        if out.return_code:
            fail("failed %s: %s" % (args, out.stderr))

    _generate_build_files(ctx, binjar, srcjar)

maven_jar = repository_rule(
    attrs = {
        "artifact": attr.string(mandatory = True),
        "attach_source": attr.bool(default = True),
        "exclude": attr.string_list(),
        "repository": attr.string(default = MAVEN_CENTRAL),
        "sha1": attr.string(),
        "src_sha1": attr.string(),
        "unsign": attr.bool(default = False),
        "exports": attr.string_list(),
        "deps": attr.string_list(),
        "_download_script": attr.label(default = Label("//tools:download_file.py")),
    },
    local = True,
    implementation = _maven_jar_impl,
)
