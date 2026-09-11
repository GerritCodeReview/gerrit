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

import logging
import os

from datetime import datetime

from gerrit.tasks.abstract import ProjectTaskRunner, Step

from . import repo

LOG = logging.getLogger(__name__)


class BackupPackedRefs(Step):
    def __init__(self, backup_suffix):
        self.backup_suffix = backup_suffix

    def run(self, repo_dir):
        os.link(
            os.path.join(repo_dir, "packed-refs"),
            os.path.join(repo_dir, f"packed-refs-{self.backup_suffix}"),
        )


class GitPackRefs(ProjectTaskRunner):
    def __init__(self):
        timestamp = datetime.now().timestamp()
        super().__init__(
            "pack-refs",
            [
                BackupPackedRefs(f"{timestamp}-before"),
            ],
            [
                BackupPackedRefs(f"{timestamp}-after"),
            ],
        )

    def _task(self, repo_dir=None):
        loose_ref_count = sum(
            len(files) for _, _, files in os.walk(os.path.join(repo_dir, "refs"))
        )

        if loose_ref_count == 0:
            LOG.info("No loose refs found. Skipping repacking.")
            return

        LOG.info("Found %s loose refs -> pack all refs", loose_ref_count)

        try:
            repo.pack_refs(repo_dir, all=True)
        except repo.GitCommandException as e:
            LOG.error("Failed to pack refs in %s", repo_dir)
            raise e

        LOG.info("Finished packing refs in %s", repo_dir)
