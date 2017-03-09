load("//tools/bzl:maven_jar.bzl", "maven_jar", "GERRIT", "MAVEN_LOCAL", "MAVEN_CENTRAL")

JGIT_VERS = "4.6.1.201703071140-r.123-g5094c1a5c"

DOC_VERS = "4.6.0.201612231935-r" # Set to JGIT_VERS unless using a snapshot

JGIT_DOC_URL = "http://download.eclipse.org/jgit/site/" + DOC_VERS + "/apidocs"

JGIT_REPO = GERRIT # Leave here even if set to MAVEN_CENTRAL.

JGIT_SHA1 = "f53fd543dfec31c3dda4bd1a3e5babfdf87c94cd"

JGIT_SRC_SHA1 = "78691302c3d232fafd737f95b89eea3a194ef840"

JGIT_SERVLET_SHA1 = "5049df9364054e5dce91a27af7bd588770b0a9e3"

JGIT_ARCHIVE_SHA1 = "f4d8ee1a86c275e762ecadcb0d51e90842afff8e"

JGIT_JUNIT_SHA1 = "6708db1f8df3daff2c55b525797b6b2cbd863611"
