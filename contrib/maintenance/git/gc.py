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

import abc
import logging
import os

from datetime import datetime, timedelta
from glob import glob
from pathlib import Path

from .config import GitConfigReader
from . import repo

LOG = logging.getLogger(__name__)

AGGRESSIVE_FLAG = "--aggressive"
MAX_AGE_GC_LOCK = timedelta(hours=12)
MAX_AGE_EMPTY_REF_DIRS = timedelta(hours=1)
MAX_AGE_INCOMING_PACKS = timedelta(days=1)
MAX_LOOSE_REF_COUNT = 10
PACK_PATH = "objects/pack"
PRESERVED_PACK_PATH = f"{PACK_PATH}/preserved"


class Util:
    @staticmethod
    def is_file_stale(file, max_age):
        return datetime.fromtimestamp(os.stat(file).st_mtime) + max_age < datetime.now()


class GCStep(abc.ABC):
    def __init__(self, git_config: GitConfigReader):
        self.git_config = git_config

    @abc.abstractmethod
    def run(self, repo_dir):
        pass


class GCLockHandlingInitStep(GCStep):
    def run(self, repo_dir):
        gc_lock_path = os.path.join(repo_dir, "gc.pid")
        if os.path.exists(gc_lock_path) and Util.is_file_stale(
            gc_lock_path, MAX_AGE_GC_LOCK
        ):
            LOG.warning(
                "Pruning stale 'gc.pid' lock file older than %s min: %s",
                MAX_AGE_GC_LOCK.min,
                gc_lock_path,
            )
            os.remove(gc_lock_path)


class PreservePacksInitStep(GCStep):
    def run(self, repo_dir):
        with GitConfigReader(
            os.path.join(repo_dir, "config"), self.git_config
        ) as config_reader:
            is_prune_preserved = config_reader.get("gc", None, "prunepreserved", False)
            is_preserve_old_packs = config_reader.get(
                "gc", None, "preserveoldpacks", False
            )

        if is_prune_preserved:
            self._prune_preserved(repo_dir)

        if is_preserve_old_packs:
            self._preserve_packs(repo_dir)

    def _prune_preserved(self, repo_dir):
        full_preserved_pack_path = os.path.join(repo_dir, PRESERVED_PACK_PATH)
        if os.path.exists(full_preserved_pack_path):
            LOG.info("Pruning old preserved packs.")
            count = 0
            for file in os.listdir(full_preserved_pack_path):
                if file.endswith(".old-pack") or file.endswith(".old-idx"):
                    count += 1
                    full_old_pack_path = os.path.join(full_preserved_pack_path, file)
                    LOG.debug("Deleting %s", full_old_pack_path)
                    os.remove(full_old_pack_path)
            LOG.info("Done pruning %d old preserved packs.", count)

    def _preserve_packs(self, repo_dir):
        full_pack_path = os.path.join(repo_dir, PACK_PATH)
        full_preserved_pack_path = os.path.join(repo_dir, PRESERVED_PACK_PATH)
        if not os.path.exists(full_preserved_pack_path):
            os.makedirs(full_preserved_pack_path)
        LOG.info("Preserving packs.")
        count = 0
        for file in os.listdir(full_pack_path):
            full_file_path = os.path.join(full_pack_path, file)
            filename, ext = os.path.splitext(file)
            if (
                os.path.isfile(full_file_path)
                and filename.startswith("pack-")
                and ext in [".pack", ".idx"]
            ):
                LOG.debug("Preserving pack %s", file)
                os.link(
                    os.path.join(full_pack_path, file),
                    os.path.join(
                        full_preserved_pack_path,
                        self._get_preserved_packfile_name(file),
                    ),
                )
                if ext == ".pack":
                    count += 1
        LOG.info("Preserved %d packs", count)

    def _get_preserved_packfile_name(self, file):
        filename, ext = os.path.splitext(file)
        return f"{filename}.old-{ext[1:]}"


class DeleteEmptyRefDirsCleanupStep(GCStep):
    def run(self, repo_dir):
        refs_path = os.path.join(repo_dir, "refs")
        self.to_delete = {}
        for dir, dirnames, filenames in os.walk(refs_path, topdown=False):
            relative = os.path.relpath(dir, refs_path)
            depth = len(relative.split(os.sep))
            if (
                not self.listdir(dir)
                and depth >= 2
                and Util.is_file_stale(dir, MAX_AGE_EMPTY_REF_DIRS)
            ):
                LOG.info("Queuing empty ref directory for deletion: %s", dir)
                self.to_delete[dir] = None

        for d in self.to_delete:
            LOG.info("Deleting %s", d)
            self.rmdir(d)

    def listdir(self, dir):
        children = (str(e) for e in Path(dir).iterdir())
        return set(children) - self.to_delete.keys()

    def rmdir(self, dir):
        try:
            os.rmdir(dir)
        except (FileNotFoundError, OSError) as e:
            LOG.warning("Couldn't delete %s: %s", dir, e)


class DeleteStaleIncomingPacksCleanupStep(GCStep):
    def run(self, repo_dir):
        objects_path = os.path.join(repo_dir, "objects")
        for file in glob(os.path.join(objects_path, "incoming_*.pack")):
            if Util.is_file_stale(file, MAX_AGE_INCOMING_PACKS):
                LOG.warning(
                    "Pruning stale incoming pack/index file older than %d days: %s",
                    MAX_AGE_INCOMING_PACKS.days,
                    file,
                )
                os.remove(file)


class PackAllRefsAfterStep(GCStep):
    def run(self, repo_dir):
        loose_ref_count = 0
        for _, _, files in os.walk(os.path.join(repo_dir, "refs"), topdown=True):
            loose_ref_count += len([file for file in files])
        if loose_ref_count > MAX_LOOSE_REF_COUNT:
            repo.pack_refs(repo_dir, all=True)
            LOG.info("Found %d loose refs -> pack all refs", loose_ref_count)
        else:
            LOG.info(
                "Found less than %d refs -> skipping pack all refs"
                % MAX_LOOSE_REF_COUNT
            )


class GitGarbageCollectionProvider:
    @staticmethod
    def get(pack_refs=True, git_config=None):
        init_steps = [
            GCLockHandlingInitStep(git_config),
            PreservePacksInitStep(git_config),
        ]
        after_steps = [
            DeleteEmptyRefDirsCleanupStep(git_config),
            DeleteStaleIncomingPacksCleanupStep(git_config),
        ]

        if pack_refs:
            after_steps.append(PackAllRefsAfterStep(git_config))

        return GitGarbageCollection(init_steps, after_steps, git_config)


class GitGarbageCollection:
    def __init__(self, init_steps, after_steps, git_config=None):
        self.init_steps = init_steps
        self.after_steps = after_steps
        self.git_config = git_config

    def run(self, repo_dir=None, args=None):
        LOG.info("Started gc in %s", repo_dir)
        if not repo_dir:
            repo_dir = repo.git_dir()
        if not os.path.exists(repo_dir) or not os.path.isdir(repo_dir):
            LOG.error("Failed: Directory does not exist: %s", repo_dir)
            return

        for init_step in self.init_steps:
            init_step.run(repo_dir)

        if self._is_aggressive(repo_dir) and AGGRESSIVE_FLAG not in args:
            args.append(AGGRESSIVE_FLAG)

        try:
            repo.gc(repo_dir, self.git_config, args)
        except repo.GitCommandException:
            LOG.error("Failed to run gc in %s", repo_dir)

        for after_step in self.after_steps:
            after_step.run(repo_dir)

        LOG.info("Finished gc in %s", repo_dir)

    def _is_aggressive(self, project_dir):
        if os.path.exists(os.path.join(project_dir, "gc-aggressive")):
            LOG.info("Running aggressive gc in %s", project_dir)
            return True
        elif os.path.exists(os.path.join(project_dir, "gc-aggressive-once")):
            LOG.info("Running aggressive gc once in %s", project_dir)
            os.remove(os.path.join(project_dir, "gc-aggressive-once"))
            return True
        return False
