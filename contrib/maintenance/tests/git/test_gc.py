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
import unittest.mock as mock

from git.repo import CGitBackend, JGitBackend

from datetime import datetime
from pathlib import Path
from git.gc import (
    MAX_AGE_EMPTY_REF_DIRS,
    MAX_AGE_INCOMING_PACKS,
    MAX_AGE_GC_LOCK,
    MAX_LOOSE_REF_COUNT,
    DeleteStaleIncomingPacksCleanupStep,
    DeleteEmptyRefDirsCleanupStep,
    GCLockHandlingInitStep,
    GitGarbageCollection,
    GitGarbageCollectionProvider,
    PackAllRefsAfterStep,
    PreservePacksInitStep,
)
from git.config import GitConfigWriter

DOUBLE_MAX_AGE_EMPTY_REF_DIRS = 2 * MAX_AGE_EMPTY_REF_DIRS
DOUBLE_MAX_AGE_INCOMING_PACKS = 2 * MAX_AGE_INCOMING_PACKS
DOUBLE_MAX_AGE_GC_LOCK = 2 * MAX_AGE_GC_LOCK


def test_GCLockHandlingInitStep(repo):
    lock_file = os.path.join(repo, "gc.pid")
    with open(lock_file, "w") as f:
        f.write("1234")

    task = GCLockHandlingInitStep([])

    task.run(repo)
    assert os.path.exists(lock_file)

    _modify_last_modified(lock_file, DOUBLE_MAX_AGE_GC_LOCK)

    task.run(repo)
    assert not os.path.exists(lock_file)


def test_PreservePacksInitStep(repo):
    task = PreservePacksInitStep([])

    pack_path = os.path.join(repo, "objects", "pack")
    preserved_pack_path = os.path.join(pack_path, "preserved")

    fake_pack = os.path.join(pack_path, "pack-fake.pack")
    fake_preserved_pack = os.path.join(preserved_pack_path, "pack-fake.old-pack")
    fake_idx = os.path.join(pack_path, "pack-fake.idx")
    fake_preserved_idx = os.path.join(preserved_pack_path, "pack-fake.old-idx")
    fake_rev = os.path.join(pack_path, "pack-fake.rev")
    fake_preserved_rev = os.path.join(preserved_pack_path, "pack-fake.old-rev")

    Path(fake_pack).touch()
    Path(fake_idx).touch()
    Path(fake_rev).touch()

    with GitConfigWriter(os.path.join(repo, "config")) as writer:
        writer.set("gc", None, "preserveoldpacks", False)
        writer.write()

    task.run(repo)

    assert not os.path.exists(fake_preserved_pack)
    assert not os.path.exists(fake_preserved_idx)
    assert not os.path.exists(fake_preserved_rev)

    with GitConfigWriter(os.path.join(repo, "config")) as writer:
        writer.set("gc", None, "preserveoldpacks", True)
        writer.write()

    task.run(repo)

    assert os.path.exists(fake_preserved_pack)
    assert os.path.exists(fake_preserved_idx)
    assert not os.path.exists(fake_preserved_rev)

    with GitConfigWriter(os.path.join(repo, "config")) as writer:
        writer.set("gc", None, "preserveoldpacks", False)
        writer.set("gc", None, "prunepreserved", True)
        writer.write()

    task.run(repo)

    assert not os.path.exists(fake_preserved_pack)
    assert not os.path.exists(fake_preserved_idx)


def test_PreservePacksInitStepWithOverride(repo):
    task = PreservePacksInitStep(["gc.preserveOldPacks=true"])

    pack_path = os.path.join(repo, "objects", "pack")
    preserved_pack_path = os.path.join(pack_path, "preserved")

    fake_pack = os.path.join(pack_path, "pack-fake.pack")
    fake_preserved_pack = os.path.join(preserved_pack_path, "pack-fake.old-pack")
    fake_idx = os.path.join(pack_path, "pack-fake.idx")
    fake_preserved_idx = os.path.join(preserved_pack_path, "pack-fake.old-idx")

    Path(fake_pack).touch()
    Path(fake_idx).touch()

    task.run(repo)

    assert os.path.exists(fake_preserved_pack)
    assert os.path.exists(fake_preserved_idx)


def test_DeleteEmptyRefDirsCleanupStep(repo):
    delete_path = os.path.join(repo, "refs", "heads", "delete")
    os.makedirs(delete_path)
    delete_change_path = os.path.join(repo, "refs", "changes", "01", "101", "meta")
    os.makedirs(delete_change_path)
    keep_path = os.path.join(repo, "refs", "heads", "keep")
    os.makedirs(keep_path)
    Path(os.path.join(keep_path, "abcd1234")).touch()

    task = DeleteEmptyRefDirsCleanupStep([])

    task.run(repo)
    assert os.path.exists(delete_path)
    assert os.path.exists(keep_path)

    _modify_last_modified(keep_path, DOUBLE_MAX_AGE_EMPTY_REF_DIRS)
    _modify_last_modified(delete_path, DOUBLE_MAX_AGE_EMPTY_REF_DIRS)
    _modify_last_modified(delete_change_path, DOUBLE_MAX_AGE_EMPTY_REF_DIRS)
    delete_change_path_parent = Path(delete_change_path).parent
    _modify_last_modified(delete_change_path_parent, DOUBLE_MAX_AGE_EMPTY_REF_DIRS)
    task.run(repo)
    assert not os.path.exists(delete_path)
    assert not os.path.exists(delete_change_path)
    assert not os.path.exists(delete_change_path_parent)


def test_DeleteEmptyRefDirsCleanupStep_keeps_ref_dir(repo):
    refs_path = os.path.join(repo, "refs")
    heads_path = os.path.join(refs_path, "heads")
    tags_path = os.path.join(refs_path, "tags")
    delete_change_path = os.path.join(refs_path, "changes", "01", "101", "meta")
    os.makedirs(delete_change_path)
    _modify_last_modified(refs_path, DOUBLE_MAX_AGE_EMPTY_REF_DIRS)
    _modify_last_modified(heads_path, DOUBLE_MAX_AGE_EMPTY_REF_DIRS)
    _modify_last_modified(tags_path, DOUBLE_MAX_AGE_EMPTY_REF_DIRS)
    _modify_last_modified(delete_change_path, DOUBLE_MAX_AGE_EMPTY_REF_DIRS)

    task = DeleteEmptyRefDirsCleanupStep([])
    task.run(repo)

    assert not os.path.exists(delete_change_path)
    assert os.path.exists(refs_path)
    assert os.path.exists(heads_path)
    assert os.path.exists(tags_path)


def test_DeleteStaleIncomingPacksCleanupStep(repo):
    task = DeleteStaleIncomingPacksCleanupStep([])

    objects_path = os.path.join(repo, "objects")
    pack_path = os.path.join(objects_path, "pack")
    pack_file = os.path.join(pack_path, "pack-1234.pack")
    Path(pack_file).touch()
    object_shard = os.path.join(objects_path, "f8")
    os.makedirs(object_shard)
    object_file = os.path.join(objects_path, "f8", "abcd")
    Path(object_file).touch()
    incoming_pack_file = os.path.join(objects_path, "incoming_1234.pack")
    Path(incoming_pack_file).touch()

    task.run(repo)

    assert os.path.exists(pack_file)
    assert os.path.exists(object_file)
    assert os.path.exists(incoming_pack_file)

    _modify_last_modified(pack_file, DOUBLE_MAX_AGE_INCOMING_PACKS)
    _modify_last_modified(object_file, DOUBLE_MAX_AGE_INCOMING_PACKS)
    _modify_last_modified(incoming_pack_file, DOUBLE_MAX_AGE_INCOMING_PACKS)

    task.run(repo)

    assert os.path.exists(pack_file)
    assert os.path.exists(object_file)
    assert not os.path.exists(incoming_pack_file)


def test_PackAllRefsAfterStep(repo, local_repo, backend):
    test_file = Path(os.path.join(local_repo, "test.txt"))
    test_file.touch()
    backend.add(local_repo, [test_file])
    backend.commit(local_repo, "test commit")

    target_loose_ref_count = 15
    loose_ref_count = 0
    while loose_ref_count < target_loose_ref_count:
        loose_ref_count += 1
        backend.push(local_repo, "origin", f"HEAD:refs/heads/test{loose_ref_count}")

    task = PackAllRefsAfterStep([], backend=backend)
    task.run(repo)

    assert len(os.listdir(os.path.join(repo, "refs", "heads"))) == 0
    packed_refs_file = os.path.join(repo, "packed-refs")
    assert os.path.exists(packed_refs_file)
    with open(packed_refs_file, "r") as f:
        assert (
            len(f.readlines()) == target_loose_ref_count + 1
        )  # First line is a comment

    backend.push(
        local_repo, "origin", f"HEAD:refs/heads/test{target_loose_ref_count + 1}"
    )
    task.run(repo)
    assert len(os.listdir(os.path.join(repo, "refs", "heads"))) == 1
    with open(packed_refs_file, "r") as f:
        assert (
            len(f.readlines()) == target_loose_ref_count + 1
        )  # First line is a comment


@mock.patch("subprocess.run")
def test_gc_executed(mock_subproc_run, repo, backend):
    gc = GitGarbageCollection([], [], backend=backend)
    gc.run(repo)
    mock_subproc_run.assert_called()
    assert mock_subproc_run.call_count == 1


# Backend-specific tests


def test_preserve_packs_step_skipped_for_jgit():
    provider = GitGarbageCollectionProvider.get(jgit=True)
    step_types = [type(s) for s in provider.init_steps]
    assert PreservePacksInitStep not in step_types


def test_preserve_packs_step_present_for_cgit():
    provider = GitGarbageCollectionProvider.get(jgit=False)
    step_types = [type(s) for s in provider.init_steps]
    assert PreservePacksInitStep in step_types


@mock.patch("subprocess.run")
def test_gc_uses_cgit_by_default(mock_subproc_run, repo):
    gc = GitGarbageCollection([], [])
    gc.run(repo)
    calls = [str(c) for c in mock_subproc_run.call_args_list]
    assert any("git" in c and "gc" in c for c in calls)
    assert not any("jgit" in c for c in calls)


def test_gc_preserves_packs(repo, local_repo, backend):
    """End-to-end: preserved pack files appear in objects/pack/preserved/
    after gc runs with preserve packs configured.

    For CGit, PreservePacksInitStep hard-links packs into preserved/ before
    gc runs, reading gc.preserveoldpacks from the repo config.
    For JGit, jgit gc moves old packs into preserved/ natively, reading
    pack.preserveOldPacks from the repo config. gc.prunePackExpire is set to
    "now" so that freshly created packs are eligible for preservation in tests.
    """
    git = CGitBackend()
    test_file = Path(os.path.join(local_repo, "test.txt"))
    test_file.touch()
    git.add(local_repo, [test_file])
    git.commit(local_repo, "test commit")
    git.push(local_repo, "origin", "HEAD:refs/heads/main")

    # Run an initial gc to create real pack files (setup, not system under test)
    git.gc(repo)
    pack_path = os.path.join(repo, "objects", "pack")
    assert any(f.endswith(".pack") for f in os.listdir(pack_path))

    with GitConfigWriter(os.path.join(repo, "config")) as writer:
        writer.set("pack", None, "preserveoldpacks", True)
        writer.set("gc", None, "prunepackexpire", "now")
        writer.write()

    GitGarbageCollectionProvider.get(
        git_config=[], jgit=isinstance(backend, JGitBackend)
    ).run(repo)

    preserved_path = os.path.join(pack_path, "preserved")
    assert os.path.exists(preserved_path)
    preserved_files = os.listdir(preserved_path)
    assert any(f.endswith(".old-pack") for f in preserved_files)
    assert any(f.endswith(".old-idx") for f in preserved_files)


def _modify_last_modified(file, time_delta):
    file_stat = os.stat(file)
    new_mod_timestamp = datetime.timestamp(
        datetime.fromtimestamp(file_stat.st_mtime) - time_delta
    )
    os.utime(file, (file_stat.st_atime, new_mod_timestamp))
