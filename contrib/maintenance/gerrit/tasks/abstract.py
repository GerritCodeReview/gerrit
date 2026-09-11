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

import abc
from gerrit.tasks import Step
import git.repo as repo
import logging
import os.path
from pathlib import Path
import sys

sys.path.append("../..")

from git.repo import GIT_SUFFIX


LOG = logging.getLogger(__name__)


class ProjectTaskRunner(abc.ABC):
    def __init__(self, name: str, init_steps: list[Step], after_steps: list[Step]):
        self.name = name
        self.init_steps = init_steps
        self.after_steps = after_steps

    def run(self, repo_dir=None, *args, **kwargs) -> bool:
        LOG.info("Started %s in %s", self.name, repo_dir)
        if not repo_dir:
            repo_dir = repo.git_dir()
        if not os.path.exists(repo_dir) or not os.path.isdir(repo_dir):
            LOG.error("Failed: Directory does not exist: %s", repo_dir)
            return

        for init_step in self.init_steps:
            init_step.run(repo_dir)

        if not self._task(repo_dir, *args, **kwargs):
            return False

        for after_step in self.after_steps:
            after_step.run(repo_dir)

        LOG.info("Finished %s in %s", self.name, repo_dir)
        return True

    @abc.abstractmethod
    def _task(self, repo_dir: str, *args, **kwargs) -> bool:
        pass


class BatchProjectTask:
    def __init__(self, site: Path, projects: list[str], runner: ProjectTaskRunner):
        self.runner = runner

        base_path = site.get_base_path()
        self.project_paths = [
            os.path.join(base_path, project + GIT_SUFFIX) for project in projects
        ]

    def run(self, *args, **kwargs) -> bool:
        failures = 0
        for project_path in self.project_paths:
            if not self.runner.run(project_path, *args, **kwargs):
                failures += 1

        return failures == 0
