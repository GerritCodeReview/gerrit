def _update_links_impl(ctx):
    dir_name = ctx.label.name
    output_files = []
    input_js_files = []
    output_js_files = []
    js_files_args = ctx.actions.args()
    js_files_args.set_param_file_format("multiline")
    js_files_args.use_param_file("%s", use_always = True)

    for f in ctx.files.srcs:
        output_file = ctx.actions.declare_file(dir_name + "/" + f.path)
        output_files.append(output_file)
        if f.extension == "html":
            input_js_files.append(f)
            output_js_files.append(output_file)
            js_files_args.add(f)
            js_files_args.add(output_file)
        else:
            ctx.actions.expand_template(
                output = output_file,
                template = f,
                substitutions = {},
            )

    ctx.actions.run(
        executable = ctx.executable._updater,
        outputs = output_js_files,
        inputs = input_js_files + [ctx.file.redirects],
        arguments = [js_files_args, ctx.file.redirects.path],
    )
    return [DefaultInfo(files = depset(output_files))]

update_links = rule(
    implementation = _update_links_impl,
    attrs = {
        "srcs": attr.label_list(allow_files = True),
        "redirects": attr.label(allow_single_file = True, mandatory = True),
        "_updater": attr.label(
            default = ":links-updater-bin",
            executable = True,
            cfg = "host",
        ),
    },
)

def _get_node_modules_root(node_modules):
    if node_modules == None or len(node_modules) == 0:
        return None

    node_module_root = node_modules[0].label.workspace_root
    for target in node_modules:
        if target.label.workspace_root != node_module_root:
            fail("Only one node_modules workspace can be used")
    return node_module_root + "/"

def _get_relative_path(file, root):
    root_len = len(root)
    if file.path.startswith(root):
        return file.path[root_len - 1:]
    else:
        fail("The file '%s' is not under the root '%s'." % (file.path, root))

def _copy_file(ctx, src, target):
    f = ctx.actions.declare_file(target)
    ctx.actions.expand_template(
        output = output_file,
        template = f,
        substitutions = {},
    )
    return f

def _process_file(ctx, file, root_path, target_dir_name, html_files_dict, js_files_dict):
    target_name = dir_name + _get_relative_path(f, root_path)
    if file.extension() == "html":
        html_output_file = ctx.actions.declare_file(target_name + "_gen.html")
        js_output_file = ctx.actions.declare_file(target_name + "_gen.js")
        html_files_dict.update([[f.path, {html: html_output_file, js: js_output_file}]])
    elif file.extension() == "js":
        js_output_file = ctx.actions.declare_file(target_name + "_gen.js")
        js_files_dict.update([[f.path, {js: js_output_file}]])
    else:
        output_file = ctx.actions.declare_file(target_name)
        _copy_file(ctx, file, output_file)

def _prepare_for_bundling_impl(ctx):
    dir_name = ctx.label.name
    all_output_files = []

    node_modules_root = _get_node_modules_root(ctx.attr.node_modules)

    html_files_dict = dict()
    js_files_dict = dict()

    root_path = ctx.bin_dir.path + "/" + ctx.attr.root_path
    if not root_path.endswith("/"):
        root_path = root_path + "/"

    for f in ctx.files.srcs:
        _process_file(ctx, f, root_path, dir_name, html_files_dict, js_files_dict)

    for f in ctx.files.additional_node_modules_to_preprocess:
        _process_file(ctx, f, node_modules_root, dir_name, html_files_dict, js_files_dict)

    for f in ctx.files.node_modules:
        target_name = dir_name + _get_relative_path(f, node_modules_root)
        if html_files_dict.get(f.path) != None or js_files_dict.get(f.path) != None:
            continue
        all_output_files.append(_copy_file(ctx, f, target_name))

    preprocessed_output_files = []
    html_files_args = ctx.actions.args()
    html_files_args.set_param_file_format("multiline")
    html_files_args.use_param_file("%s", use_always = True)

    for src_path, output_files in html_files_dict.items():
        args.add(src_path)
        args.add(output_files.html)
        args.add(output_files.js)
        preprocessed_output_files.add(output_files.html)
        preprocessed_output_files.add(output_files.js)

    for src_path, output_files in js_files_dict.items():
        args.add(src_path)
        args.add(output_files.js)
        preprocessed_output_files.add(output_files.js)

    all_output_files.expand

    return [DefaultInfo(files = depset(all_output_files))]

prepare_for_bundling = rule(
    implementation = _prepare_for_bundling_impl,
    attrs = {
        "srcs": attr.label_list(allow_files = True),
        "node_modules": attr.label_list(allow_files = True),
        #        "_preprocessor": attr.label(
        #            default = ":preprocessor-bin",
        #            executable = True,
        #            cfg = "host",
        #        ),
        "additional_node_modules_to_preprocess": attr.label_list(allow_files = True),
        "root_path": attr.string(),
    },
)
