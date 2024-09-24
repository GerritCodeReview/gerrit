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


def git_dir():
    try:
        return (
            subprocess.run(
                ["git", "rev-parse", "--git-dir"], capture_output=True, check=True
            )
            .stdout.decode()
            .strip()
        )
    except subprocess.CalledProcessError:
        raise GitCommandException("Unable to find .git directory.")


def commit_id(repo_dir, ref="HEAD"):
    try:
        cmd = ["git", "rev-parse", "--short", ref]
        return (
            subprocess.run(cmd, cwd=repo_dir, capture_output=True, check=True)
            .stdout.decode()
            .strip()
        )
    except subprocess.CalledProcessError:
        raise GitCommandException("Unable to parse current commit ID.")


def add(repo_dir, files=None):
    if not files:
        files = ["."]
    try:
        cmd = ["git", "add"]
        cmd.extend(files)
        subprocess.run(cmd, cwd=repo_dir, capture_output=True, check=True)
    except subprocess.CalledProcessError:
        raise GitCommandException("Unable to add files to index.")


def commit(repo_dir, message):
    try:
        cmd = ["git", "commit", "-m", message]
        subprocess.run(cmd, cwd=repo_dir, capture_output=True, check=True)
    except subprocess.CalledProcessError:
        raise GitCommandException("Unable to commit.")


def push(repo_dir, remote, refspec):
    try:
        cmd = ["git", "push", remote, refspec]
        subprocess.run(cmd, cwd=repo_dir, capture_output=True, check=True)
    except subprocess.CalledProcessError:
        raise GitCommandException("Unable to push.")


def clone(url, target_dir=""):
    try:
        cmd = ["git", "clone", url, target_dir]
        subprocess.run(cmd, capture_output=True, check=True)
        if target_dir:
            return target_dir

        repo_name = url.split("/")[-1]
        if repo_name.endswith(GIT_SUFFIX):
            repo_name = repo_name[: -len(GIT_SUFFIX)]
        return repo_name
    except subprocess.CalledProcessError:
        raise GitCommandException(f"Unable to clone repo {url}.")


def init(base_dir, repo_name, bare=False):
    try:
        cmd = ["git", "init"]
        if bare:
            cmd.append("--bare")
        cmd.append(os.path.join(base_dir, repo_name))
        subprocess.run(cmd, cwd=base_dir, capture_output=True, check=True)
    except subprocess.CalledProcessError:
        raise GitCommandException(f"Unable to initialize git repo {repo_name}.")


def pack_refs(repo_dir, all=False):
    command = "git pack-refs"
    if all:
        command += " --all"
    try:
        subprocess.run(command, cwd=repo_dir, shell=True, check=True)
    except subprocess.CalledProcessError as e:
        if e.stdout:
            LOG.info(e.stdout)
        if e.stderr:
            LOG.error(e.stderr)
        raise GitCommandException(f"Failed to pack refs in {repo_dir}")


def gc(repo_dir, args):
    cmd = "git gc"
    if args:
        cmd = cmd + " " + " ".join(args)
    try:
        # Git gc requires a shell to output logs, i.e. `shell` has to be `True`
        subprocess.run(cmd, cwd=repo_dir, shell=True, check=True)
    except subprocess.CalledProcessError as e:
        if e.stdout:
            LOG.info(e.stdout)
        if e.stderr:
            LOG.error(e.stderr)
        raise GitCommandException(f"Failed to run gc in {repo_dir}")
