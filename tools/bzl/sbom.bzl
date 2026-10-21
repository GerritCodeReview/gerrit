"""Auto-deriving CycloneDX 1.7 SBOM for a Bazel target, via a dep-graph aspect.

An aspect walks the target's dependency closure and reads rules_jvm_external's
maven_coordinates and maven_sha256 tags off each jvm_import. That SHA-256 is the
hash of the artifact as consumed from the Maven repository, the identity a
vulnerability scanner matches. The value rides a provider into the generating
action, where a Python action emits the JSON.

Pointed at a WAR target with its labels manifest, the SBOM covers every jar in
the WAR plus the compile-only tools it pulls, each marked shipped or compile-only
by the producing target's owner label.
"""

SbomInfo = provider(
    doc = "Maven nodes collected from a target's dependency closure.",
    fields = {
        "coord": "this target's maven coordinate, or None if not a jvm_import",
        "nodes": "depset of struct(label, coord, sha256, deps) over the closure",
    },
)

def _tag(tags, prefix):
    for t in tags:
        if t.startswith(prefix):
            return t[len(prefix):]
    return None

def _attr_targets(value):
    """Flatten a rule attribute value to the Targets it points at.

    Dependency edges arrive as a single Target (label), a list of Targets
    (label_list), or a dict whose keys/values are Targets (label_keyed_string_
    dict). Everything else carries no edge.
    """
    if type(value) == "Target":
        return [value]
    if type(value) == type([]):
        return [v for v in value if type(v) == "Target"]
    if type(value) == type({}):
        out = []
        for k, v in value.items():
            for entry in (k, v):
                if type(entry) == "Target":
                    out.append(entry)
                elif type(entry) == type([]):
                    out.extend([i for i in entry if type(i) == "Target"])
        return out
    return []

def _sbom_aspect_impl(target, ctx):
    tags = getattr(ctx.rule.attr, "tags", None) or []
    coord = _tag(tags, "maven_coordinates=")
    sha256 = _tag(tags, "maven_sha256=")

    # Follow every attribute (attr_aspects = ["*"]) to reach what deps() does,
    # including annotation processors pulled in only via plugins. Dedup by label.
    transitive = []
    direct_dep_coords = []
    seen = {}
    for name in dir(ctx.rule.attr):
        for dep in _attr_targets(getattr(ctx.rule.attr, name)):
            if SbomInfo not in dep:
                continue
            key = str(dep.label)
            if key in seen:
                continue
            seen[key] = True
            transitive.append(dep[SbomInfo].nodes)
            if dep[SbomInfo].coord:
                direct_dep_coords.append(dep[SbomInfo].coord)

    direct = []
    if coord:
        direct.append(struct(
            label = str(target.label),
            coord = coord,
            sha256 = sha256 if sha256 else "",
            # Tuple, not list: depset elements must be immutable.
            deps = tuple(direct_dep_coords),
        ))

    return [SbomInfo(
        coord = coord,
        nodes = depset(direct = direct, transitive = transitive),
    )]

_sbom_aspect = aspect(
    implementation = _sbom_aspect_impl,
    attr_aspects = ["*"],
    provides = [SbomInfo],
    apply_to_generating_rules = True,
)

def _sbom_impl(ctx):
    nodes = ctx.attr.target[SbomInfo].nodes.to_list()

    facts = ctx.actions.declare_file(ctx.label.name + ".graph.json")
    ctx.actions.write(
        output = facts,
        content = json.encode([
            {
                "label": n.label,
                "coord": n.coord,
                "sha256": n.sha256,
                "deps": list(n.deps),
            }
            for n in nodes
        ]),
    )

    out = ctx.actions.declare_file(ctx.label.name + ".cyclonedx.json")
    args = ctx.actions.args()
    args.add("--graph-facts", facts)
    args.add("--output", out)
    args.add("--target", str(ctx.attr.target.label))
    args.add("--product-name", ctx.attr.product_name)
    args.add("--property-namespace", ctx.attr.namespace)
    args.add("--product-version-file", ctx.file.version_file)
    inputs = [facts, ctx.file.version_file]
    if ctx.file.war_labels:
        args.add("--war-labels", ctx.file.war_labels)
        inputs.append(ctx.file.war_labels)

    ctx.actions.run(
        executable = ctx.executable._generator,
        arguments = [args],
        inputs = inputs,
        outputs = [out],
        mnemonic = "Sbom",
        progress_message = "Generating SBOM %s" % ctx.label,
    )

    # Only the SBOM is a deliverable. The facts file is an intermediate input
    # to the generator, built on demand but not surfaced as an output.
    return [DefaultInfo(files = depset([out]))]

sbom = rule(
    implementation = _sbom_impl,
    doc = "CycloneDX 1.7 SBOM for target's Maven dependency closure.",
    attrs = {
        "target": attr.label(
            mandatory = True,
            aspects = [_sbom_aspect],
            doc = "label whose dependency closure is described",
        ),
        "war_labels": attr.label(
            allow_single_file = True,
            doc = "the WAR's *.war.labels.txt (path<TAB>owner label), to mark " +
                  "shipped vs compile-only by owner, not by jar name",
        ),
        "version_file": attr.label(
            allow_single_file = True,
            default = "//:version.txt",
        ),
        "product_name": attr.string(default = "gerrit-war"),
        "namespace": attr.string(default = "gerrit"),
        "_generator": attr.label(
            default = "//tools/sbom:sbom_from_graph",
            executable = True,
            cfg = "exec",
        ),
    },
)
