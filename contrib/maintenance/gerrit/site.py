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

import sys

sys.path.append("..")

import os

from git.config import GitConfigReader
from git.repo import GIT_SUFFIX


class Site:
    def __init__(self, path):
        self.path = path
        self.base_path = None

    def get_etc_path(self):
        return os.path.join(self.path, "etc")

    def get_base_path(self):
        if not self.base_path:
            with GitConfigReader(
                os.path.join(self.get_etc_path(), "gerrit.config")
            ) as cfg:
                config_base_path = cfg.get("gerrit", None, "basePath", "git")
                if os.path.isabs(config_base_path):
                    self.basePath = config_base_path
                else:
                    self.basePath = os.path.join(self.path, config_base_path)

        return self.basePath

    def get_projects(self, excludes=None):
        for current, dirs, _ in os.walk(self.get_base_path(), topdown=True):
            if os.path.splitext(current)[1] != GIT_SUFFIX:
                continue

            dirs.clear()
            project = f"{current[len(self.get_base_path()) + 1:-len(GIT_SUFFIX)]}"
            if excludes and project in excludes:
                continue

            yield project
