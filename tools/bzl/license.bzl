
def license_map(name, target):
    """Generate XML for all targets that depend directly on a LICENSE file"""
    native.genquery(
        name = name + ".xml",
        scope = [ target, ],

        # Find everything that depends on a license file, but remove
        # the license files themselves from this list.
        expression = 'rdeps(%s, filter("//lib:LICENSE.*", deps(%s)),1) - filter("//lib:LICENSE.*", deps(%s))' % (target, target, target),

        # We are interested in the edges of the graph ({java_library,
        # license-file} tuples).  'query' provides this in the XML output.
        opts = [ "--output=xml"],
    )

    # post process the XML into our favorite format.
    native.genrule(
        name = "gen_license_txt_" + name,
        cmd = "python $(location //tools/bzl:license-map.py) $(location :%s.xml) > $@" % name,
        outs = [ name + ".txt",],
        tools = [ "//tools/bzl:license-map.py", name + ".xml"])

def license_test(name, target):
    """Generate XML for all targets that depend directly on a LICENSE file"""
    txt = name + "-forbidden.txt"

    # fully qualify target name.
    if target[0] not in ":/":
        target = ":" + target
    if target[0] != "/":
        target = "//" + PACKAGE_NAME + target

    forbidden = "//lib:LICENSE-DO_NOT_DISTRIBUTE"
    native.genquery(
        name = txt,
        scope = [ target, forbidden ],
        # Find everything that depends on a license file, but remove
        # the license files themselves from this list.
        expression = 'rdeps(%s, "%s", 1) - rdeps(%s, "%s", 0)' % (target, forbidden, target, forbidden),
    )
    native.sh_test(
        name = name,
        srcs = [ "//tools/bzl:test_empty.sh" ],
        args  = [ "$(location :%s)" % txt],
        data = [ txt ],
    )
