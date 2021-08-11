"""This file contains rules to generate and test the license map"""

def normalize_target_name(target):
    return target.replace("//", "").replace("/", "__").replace(":", "___")

def license_map(name, targets = [], opts = [], json_maps = [], **kwargs):
    """Generate text represantation for pacakges' and libs' licenses

    Args:
        name: of the rule
        targets: list of targets for which licenses should be added the output file.
            The list must not include targets, for which json_map is passed in json_maps parameter
        opts: command line options for license-map.py tool
        json_maps: list of json files. Such files can be produced by node_modules_licenses rule
            for node_modules licenses.
        **kwargs: Args passed through to genrule

    Generate: text file with the name
        gen_license_txt_{name}

    """

    # Generate XML for all targets that depend directly on a LICENSE file
    xmls = []
    tools = ["//tools/bzl:license-map.py", "//lib:all-licenses"]
    for target in targets:
        subname = name + "_" + normalize_target_name(target) + ".xml"
        xmls.append("$(location :%s)" % subname)
        tools.append(subname)
        native.genquery(
            name = subname,
            scope = [target],

            # Find everything that depends on a license file, but remove
            # the license files themselves from this list.
            expression = 'rdeps(%s, filter("//lib:LICENSE.*", deps(%s)),1) - filter("//lib:LICENSE.*", deps(%s))' % (target, target, target),

            # We are interested in the edges of the graph ({java_library,
            # license-file} tuples).  'query' provides this in the XML output.
            opts = ["--output=xml"],
        )

    # Add all files from the json_maps list to license-map.py command-line arguments
    json_maps_locations = []

    for json_map in json_maps:
        json_maps_locations.append("--json-map=$(location %s)" % json_map)
        tools.append(json_map)

    # post process the XML into our favorite format.
    native.genrule(
        name = "gen_license_txt_" + name,
        cmd = "python3 $(location //tools/bzl:license-map.py) %s %s %s > $@" % (" ".join(opts), " ".join(json_maps_locations), " ".join(xmls)),
        outs = [name + ".gen.txt"],
        tools = tools,
        **kwargs
    )

def license_test(name, target):
    """Make sure a target doesn't depend on DO_NOT_DISTRIBUTE license"""
    txt = name + "-forbidden.txt"

    # fully qualify target name.
    if target[0] not in ":/":
        target = ":" + target
    if target[0] != "/":
        target = "//" + native.package_name() + target

    forbidden = "//lib:LICENSE-DO_NOT_DISTRIBUTE"
    native.genquery(
        name = txt,
        scope = [target, forbidden],
        # Find everything that depends on a license file, but remove
        # the license files themselves from this list.
        expression = 'rdeps(%s, "%s", 1) - rdeps(%s, "%s", 0)' % (target, forbidden, target, forbidden),
    )
    native.sh_test(
        name = name,
        srcs = ["//tools/bzl:test_license.sh"],
        args = ["$(location :%s)" % txt],
        data = [txt],
    )
