# Copyright (C) 2024 The Android Open Source Project
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

import os
import pytest

from gerrit.site import Site
from git.repo import init, GIT_SUFFIX

REPOSITORIES = ["All-Projects", "All-Users", "test", "nested/repo"]


@pytest.fixture(scope="function")
def site(tmp_path_factory):
    site = tmp_path_factory.mktemp("site")
    base_path = os.path.join(site, "git")
    os.makedirs(base_path)
    for repo in REPOSITORIES:
        init(base_path, repo + GIT_SUFFIX, bare=True)
    etc_path = os.path.join(site, "etc")
    os.makedirs(etc_path)
    with open(os.path.join(etc_path, "gerrit.config"), "w") as f:
        f.write(
            """
          [gerrit]
            basePath = git
        """
        )
    return site


def test_get_projects(site):
    site = Site(site)
    assert REPOSITORIES.sort() == list(site.get_projects()).sort()
    assert "test" not in list(site.get_projects(excludes=["test"]))
