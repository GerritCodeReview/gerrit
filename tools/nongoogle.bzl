load("//tools/bzl:maven_jar.bzl", "GERRIT", "MAVEN_LOCAL", "maven_jar")

def declare_nongoogle_deps():
  """loads dependencies that are not used at Google.

  These are exempt from library compliance review.
  """
  # Transitive dependency of commons-compress
  maven_jar(
      name = "tukaani-xz",
      artifact = "org.tukaani:xz:1.6",
      sha1 = "05b6f921f1810bdf90e25471968f741f87168b64",
  )
