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

from pathlib import Path
import sys

from git.pack_refs import GitPackRefs

sys.path.append("../..")

from gerrit.tasks.abstract import BatchProjectTask
from git.gc import GitGarbageCollectionProvider


class BatchGitGarbageCollection(BatchProjectTask):
    def __init__(
        self,
        site: Path,
        projects: list[str],
        pack_refs: bool = True,
        git_config: str = None,
    ):
        super().__init__(
            site, projects, GitGarbageCollectionProvider.get(pack_refs, git_config)
        )


class BatchGitPackRefs(BatchProjectTask):
    def __init__(
        self,
        site: Path,
        projects: list[str],
    ):
        super().__init__(site, projects, GitPackRefs())
