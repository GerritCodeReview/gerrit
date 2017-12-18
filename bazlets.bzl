NAME = "com_googlesource_gerrit_bazlets"

def load_bazlets(
    commit = None,
    local_path = None
  ):
  if not local_path:
      native.git_repository(
          name = NAME,
          remote = "https://gerrit.googlesource.com/bazlets",
          commit = commit,
      )
  else:
      native.local_repository(
          name = NAME,
          path = local_path,
      )
