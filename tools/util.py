# Copyright (C) 2013 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from os import path

REPO_ROOTS = {
  'ECLIPSE': 'https://repo.eclipse.org/content/groups/releases',
  'ECLIPSE_EGIT': 'https://repo.eclipse.org/content/repositories/egit-releases',
  'GERRIT': 'https://gerrit-maven.storage.googleapis.com',
  'GERRIT_API': 'https://gerrit-api.commondatastorage.googleapis.com/release',
  'JENKINS': 'https://repo.jenkins-ci.org/artifactory/public',
  'MAVEN_CENTRAL': 'https://repo1.maven.org/maven2',
  'MAVEN_LOCAL': 'file://' + path.expanduser('~/.m2/repository'),
  'MAVEN_SNAPSHOT': 'https://ossrh-staging-api.central.sonatype.com/content/repositories/snapshots',
}


def resolve_url(url, redirects):
    """ Resolve URL of a Maven artifact.

    prefix:path is passed as URL. prefix identifies known or custom
    repositories that can be rewritten in redirects set, passed as
    second arguments.

    A special case is supported, when prefix neither exists in
    REPO_ROOTS, no in redirects set: the url is returned as is.
    This enables plugins to pass custom maven_repository URL as is
    directly to maven_jar().

    Returns a resolved path for Maven artifact.
    """
    s = url.find(':')
    if s < 0:
        return url
    scheme, rest = url[:s], url[s+1:]
    if scheme in redirects:
        root = redirects[scheme]
    elif scheme in REPO_ROOTS:
        root = REPO_ROOTS[scheme]
    else:
        return url
    root = root.rstrip('/')
    rest = rest.lstrip('/')
    return '/'.join([root, rest])


def hash_file(hash_obj, path):
    """Hash the contents of a file.

    Args:
      hash_obj: an open hash object, e.g. hashlib.sha1().
      path: path to the file to hash.

    Returns:
      The passed-in hash_obj.
    """
    with open(path, 'rb') as f:
        while True:
            b = f.read(8192)
            if not b:
                break
            hash_obj.update(b)
    return hash_obj
