
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
