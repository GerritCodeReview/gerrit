
def unsign_jars(name, deps, **kwargs):
  """unsign_jars collects its dependencies into a single java_import.

  As a side effect, the signature is removed.
  """
  native.java_binary(
    name = name + '-unsigned-binary',
    runtime_deps = deps,
    main_class = 'dummy'
  )

  native.java_import(
    name = name,
    jars = [ name + '-unsigned-binary_deploy.jar' ],
    **kwargs)
