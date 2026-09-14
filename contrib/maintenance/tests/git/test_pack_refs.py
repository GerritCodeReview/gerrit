# Copyright (C) 2026 The Android Open Source Project
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
import unittest.mock as mock

import pytest

from pathlib import Path
from git.pack_refs import BackupPackedRefs, GitPackRefs
from git.repo import CGitBackend, JGitBackend


@pytest.fixture(scope="function")
def repo_with_loose_refs(repo, local_repo):
    git = CGitBackend()
    test_file = Path(os.path.join(local_repo, "test.txt"))
    test_file.touch()
    git.add(local_repo, [test_file])
    git.commit(local_repo, "test commit")
    git.push(local_repo, "origin", "HEAD:refs/heads/testbranch")
    # BackupPackedRefs requires packed-refs to exist before the task runs
    Path(os.path.join(repo, "packed-refs")).touch()
    yield repo


def test_BackupPackedRefs_creates_hardlink(repo):
    packed_refs = os.path.join(repo, "packed-refs")
    Path(packed_refs).touch()

    task = BackupPackedRefs("test-backup")
    task.run(repo)

    backup = os.path.join(repo, "packed-refs-test-backup")
    assert os.path.exists(backup)
    # Verify it is a hard link (same inode)
    assert os.stat(packed_refs).st_ino == os.stat(backup).st_ino


def test_GitPackRefs_skips_when_no_loose_refs(repo, backend):
    # BackupPackedRefs requires packed-refs to exist
    Path(os.path.join(repo, "packed-refs")).touch()

    with mock.patch.object(type(backend), "pack_refs") as mock_pack_refs:
        task = GitPackRefs(jgit=isinstance(backend, JGitBackend))
        task.run(repo)
        mock_pack_refs.assert_not_called()


def test_GitPackRefs_packs_when_loose_refs_exist(repo_with_loose_refs, backend):
    # Confirm there is at least one loose ref
    loose_ref_count = sum(
        len(files)
        for _, _, files in os.walk(os.path.join(repo_with_loose_refs, "refs"))
    )
    assert loose_ref_count > 0

    task = GitPackRefs(jgit=isinstance(backend, JGitBackend))
    task.run(repo_with_loose_refs)

    # After packing, refs/heads should be empty
    heads_dir = os.path.join(repo_with_loose_refs, "refs", "heads")
    assert len(os.listdir(heads_dir)) == 0

    # packed-refs file must exist
    packed_refs = os.path.join(repo_with_loose_refs, "packed-refs")
    assert os.path.exists(packed_refs)


def test_GitPackRefs_creates_before_and_after_backups(repo_with_loose_refs, backend):
    task = GitPackRefs(jgit=isinstance(backend, JGitBackend))
    task.run(repo_with_loose_refs)

    packed_refs = os.path.join(repo_with_loose_refs, "packed-refs")
    assert os.path.exists(packed_refs)

    backups = [
        f
        for f in os.listdir(repo_with_loose_refs)
        if f.startswith("packed-refs-") and f != "packed-refs"
    ]
    assert len(backups) == 2
    assert any("before" in b for b in backups)
    assert any("after" in b for b in backups)


@mock.patch("subprocess.run")
def test_GitPackRefs_calls_pack_refs_command(mock_subproc_run, repo, backend):
    loose_ref = os.path.join(repo, "refs", "heads", "main")
    Path(loose_ref).touch()

    packed_refs = os.path.join(repo, "packed-refs")
    Path(packed_refs).touch()

    task = GitPackRefs(jgit=isinstance(backend, JGitBackend))
    task.run(repo)

    mock_subproc_run.assert_called()
    calls = [str(c) for c in mock_subproc_run.call_args_list]
    assert any("pack-refs" in c for c in calls)
