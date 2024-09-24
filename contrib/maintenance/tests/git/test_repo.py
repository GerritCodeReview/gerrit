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

import git.repo


@pytest.fixture(scope="function")
def tmp_dir(tmp_path_factory):
    return tmp_path_factory.mktemp("ltmp_dir")


def test_git_init_commit(tmp_dir):
    repo_name = "repo"
    repo_path = os.path.join(tmp_dir, repo_name)
    repo_git_path = os.path.join(repo_path, ".git")

    git.repo.init(tmp_dir, repo_name)
    assert os.path.exists(repo_path)
    assert os.path.exists(repo_git_path)

    new_commit = _create_new_commit(repo_path)
    assert new_commit


def test_git_clone_push(tmp_dir, repo):
    repo_name = "repo"
    repo_path = os.path.join(tmp_dir, repo_name)
    repo_git_path = os.path.join(repo_path, ".git")

    git.repo.clone(repo, repo_path)
    assert os.path.exists(repo_path)
    assert os.path.exists(repo_git_path)

    new_commit = _create_new_commit(repo_path)
    assert new_commit

    git.repo.push(repo_path, "origin", "HEAD:refs/heads/master")
    assert git.repo.commit_id(repo, "refs/heads/master") == new_commit


def _create_new_commit(repo_path):
    with open(os.path.join(repo_path, "test.txt"), "w") as f:
        f.write("test content")

    git.repo.add(repo_path)
    git.repo.commit(repo_path, "Test commit")
    return git.repo.commit_id(repo_path)
