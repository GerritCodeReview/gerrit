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
import sys

sys.path.append("../..")

from git.repo import GIT_SUFFIX


class BatchGitGarbageCollection:
    def __init__(self, site, projects, gc_runner):
        self.site = site
        self.projects = projects
        self.gc_runner = gc_runner

    def run(self, gc_args):
        base_path = self.site.get_base_path()
        for project in self.projects:
            self.gc_runner.run(os.path.join(base_path, project + GIT_SUFFIX), gc_args)
