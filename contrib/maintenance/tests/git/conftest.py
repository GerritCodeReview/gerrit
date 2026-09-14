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

import os.path
import pytest

from git.repo import CGitBackend, JGitBackend


@pytest.fixture(scope="function")
def repo(tmp_path_factory):
    dir = tmp_path_factory.mktemp("repos")
    repo_name = "test.git"
    CGitBackend().init(dir, repo_name, bare=True)
    return os.path.join(dir, repo_name)


@pytest.fixture(scope="function")
def local_repo(tmp_path_factory, repo):
    dir = tmp_path_factory.mktemp("local.git")
    CGitBackend().clone(repo, dir)
    return dir


@pytest.fixture(params=["cgit", "jgit"])
def backend(request):
    if request.param == "cgit":
        return CGitBackend()
    return JGitBackend()
