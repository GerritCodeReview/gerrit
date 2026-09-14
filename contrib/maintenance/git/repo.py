# Copyright (C) 2018 The Android Open Source Project
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
import subprocess

LOG = logging.getLogger(__name__)

GIT_SUFFIX = ".git"


class GitCommandException(Exception):
    """Exception thrown by failed git commands."""


class GitBackend:
    def __init__(self, git_exe: str, git_config: str):
        self.git_exe = git_exe
        self.git_config = git_config

    def git_dir(self):
        try:
            return (
                subprocess.run(
                    [self.git_exe, "rev-parse", "--git-dir"],
                    capture_output=True,
                    check=True,
                )
                .stdout.decode()
                .strip()
            )
        except subprocess.CalledProcessError:
            raise GitCommandException("Unable to find .git directory.")

    def commit_id(self, repo_dir, ref="HEAD"):
        try:
            cmd = [self.git_exe, "rev-parse", "--short", ref]
            return (
                subprocess.run(cmd, cwd=repo_dir, capture_output=True, check=True)
                .stdout.decode()
                .strip()
            )
        except subprocess.CalledProcessError:
            raise GitCommandException("Unable to parse current commit ID.")

    def add(self, repo_dir, files=None):
        if not files:
            files = ["."]
        try:
            cmd = [self.git_exe, "add"]
            cmd.extend(files)
            subprocess.run(cmd, cwd=repo_dir, capture_output=True, check=True)
        except subprocess.CalledProcessError:
            raise GitCommandException("Unable to add files to index.")

    def commit(self, repo_dir, message):
        try:
            cmd = [self.git_exe, "commit", "-m", message]
            subprocess.run(cmd, cwd=repo_dir, capture_output=True, check=True)
        except subprocess.CalledProcessError:
            raise GitCommandException("Unable to commit.")

    def push(self, repo_dir, remote, refspec):
        try:
            cmd = [self.git_exe, "push", remote, refspec]
            subprocess.run(cmd, cwd=repo_dir, capture_output=True, check=True)
        except subprocess.CalledProcessError:
            raise GitCommandException("Unable to push.")

    def clone(self, url, target_dir=""):
        try:
            cmd = [self.git_exe, "clone", url, target_dir]
            subprocess.run(cmd, capture_output=True, check=True)
            if target_dir:
                return target_dir

            repo_name = url.split("/")[-1]
            if repo_name.endswith(GIT_SUFFIX):
                repo_name = repo_name[: -len(GIT_SUFFIX)]
            return repo_name
        except subprocess.CalledProcessError:
            raise GitCommandException(f"Unable to clone repo {url}.")

    def init(self, base_dir, repo_name, bare=False):
        try:
            cmd = [self.git_exe, "init"]
            if bare:
                cmd.append("--bare")
            cmd.append(os.path.join(base_dir, repo_name))
            subprocess.run(cmd, cwd=base_dir, capture_output=True, check=True)
        except subprocess.CalledProcessError:
            raise GitCommandException(f"Unable to initialize git repo {repo_name}.")

    def pack_refs(self, repo_dir, all=False):
        command = f"{self.git_exe} pack-refs"
        if all:
            command += " --all"
        try:
            subprocess.run(command, cwd=repo_dir, shell=True, check=True)
        except subprocess.CalledProcessError:
            raise GitCommandException(f"Failed to pack refs in {repo_dir}")

    def gc(self, repo_dir, git_config=None, args=None):
        cmd = f"{self.git_exe} "
        if git_config:
            cmd = cmd + "-c " + " -c ".join(git_config)
        cmd += " gc"
        if args:
            cmd = cmd + " " + " ".join(args)
        try:
            # Git gc requires a shell to output logs, i.e. `shell` has to be `True`
            subprocess.run(cmd, cwd=repo_dir, shell=True, check=True)
        except subprocess.CalledProcessError:
            raise GitCommandException(f"Failed to run gc in {repo_dir}")


class CGitBackend(GitBackend):
    """Backend that delegates to the cgit CLI available in PATH."""

    def __init__(self, git_config=None):
        super().__init__("git", git_config)


class JGitBackend(GitBackend):
    """Backend that delegates to the jgit CLI available in PATH."""

    def __init__(self, git_config=None):
        super().__init__("jgit", git_config)
