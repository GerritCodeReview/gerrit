#!/usr/bin/env python

import argparse
import re
import subprocess

from enum import Enum

EXCLUDED_SUBJECTS = {
    "AutoValue",
    "avadoc",
    "avaDoc",
    "ava-doc",
    "baz",  # bazel, bazlet(s)
    "Baz",
    "class",
    "efactor",
    "format",
    "Format",
    "getter",
    "gr-",
    "immutab",
    "IT",
    "js",
    "lint",
    "method",
    "module",
    "naming",
    "nits",
    "nongoogle",
    "prone",  # error prone &co.
    "register",
    "Register",
    "remove",
    "Remove",
    "rename",
    "Rename",
    "Revert",
    "serializ",
    "setter",
    "spell",
    "Spell",
    "test",  # testing, tests; unit or else
    "Test",
    "thread",
    "tsetse",
    "typescript",
    "version",
}

COMMIT_SHA1_PATTERN = r"^commit ([a-z0-9]+)$"
DATE_HEADER_PATTERN = r"Date: .+"
SUBJECT_SUBMODULES_PATTERN = r"^Update git submodules$"
UPDATE_SUBMODULE_PATTERN = r"\* Update ([a-z/\-]+) from branch '.+'"
SUBMODULE_SUBJECT_PATTERN = r"^- (.+)"
SUBMODULE_MERGE_PATTERN = r".+Merge .+"
ISSUE_ID_PATTERN = r"[a-zA-Z]+: [Ii]ssue ([0-9]+)"
CHANGE_ID_PATTERN = r"^Change-Id: [I0-9a-z]+$"
PLUGIN_PATTERN = r"plugins/([a-z\-]+)"
RELEASE_OPTION_PATTERN = r".+\.\.(v.+)"
RELEASE_TAG_PATTERN = r"v[0-9]+\.[0-9]+\.[0-9]+$"

ISSUE_URL = "https://bugs.chromium.org/p/gerrit/issues/detail?id="
CHECK_DISCLAIMER = "experimental and much slower"
GIT_COMMAND = "git"
UTF8 = "UTF-8"


def parse_args():
    parser = argparse.ArgumentParser(
        description="Generate an initial release notes markdown file.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "-c",
        "--check",
        dest="check",
        required=False,
        default=False,
        action="store_true",
        help=f"check commits for previous releases; {CHECK_DISCLAIMER}",
    )
    parser.add_argument("range", help="git log revision range")
    return parser.parse_args()


def check_args(options):
    if not options.check:
        return None
    release_option = re.search(RELEASE_OPTION_PATTERN, options.range)
    if release_option is None:
        print("Check option ignored; range doesn't end with release tag.")
        return None
    print(f"Check option used; {CHECK_DISCLAIMER}.")
    return release_option.group(1)


def newly_released(commit_sha1, release):
    if release is None:
        return True
    git_tag = [
        GIT_COMMAND,
        "tag",
        "--contains",
        commit_sha1,
    ]
    process = subprocess.check_output(git_tag, stderr=subprocess.PIPE, encoding=UTF8)
    verdict = True
    for line in process.splitlines():
        line = line.strip()
        if not re.match(rf"{re.escape(release)}$", line):
            # Wrongfully pushed or malformed tags ignored.
            # Preceding release-candidate (-rcN) tags treated as newly released.
            verdict = not re.match(RELEASE_TAG_PATTERN, line)
    return verdict


def open_git_log(options):
    git_log = [
        GIT_COMMAND,
        "log",
        "--no-merges",
        options.range,
    ]
    return subprocess.check_output(git_log, encoding=UTF8)


class Change:
    subject = None
    issues = set()


class Task(Enum):
    start_commit = 1
    finish_headers = 2
    capture_subject = 3
    capture_submodule = 4
    capture_submodule_subject = 5
    finish_submodule_change = 6
    finish_commit = 7


class Commit:
    sha1 = None
    subject = None
    submodule = None
    issues = set()

    def reset(self, signature, task):
        if signature is not None:
            self.sha1 = signature.group(1)
            self.subject = None
            self.submodule = None
            self.issues = set()
            return Task.finish_headers
        return task


def parse_log(process, release):
    commit = Commit()
    commits = []
    submodules = dict()
    submodule_change = None
    task = Task.start_commit
    for line in process.splitlines():
        line = line.strip()
        if not line:
            continue
        if task == Task.start_commit:
            task = commit.reset(re.search(COMMIT_SHA1_PATTERN, line), task)
        elif task == Task.finish_headers:
            if re.match(DATE_HEADER_PATTERN, line):
                task = Task.capture_subject
        elif task == Task.capture_subject:
            if re.match(SUBJECT_SUBMODULES_PATTERN, line):
                task = Task.capture_submodule
            else:
                commit.subject = line
                task = Task.finish_commit
        elif task == Task.capture_submodule:
            commit.submodule = re.search(UPDATE_SUBMODULE_PATTERN, line).group(1)
            if commit.submodule not in submodules:
                submodules[commit.submodule] = []
            task = Task.capture_submodule_subject
        elif task == Task.capture_submodule_subject:
            submodule_subject = re.search(SUBMODULE_SUBJECT_PATTERN, line)
            if submodule_subject is not None:
                if not re.match(SUBMODULE_MERGE_PATTERN, line):
                    submodule_change = change(submodule_subject, submodules, commit)
                    task = Task.finish_submodule_change
            else:
                task = update_task(line, commit, task)
        elif task == Task.finish_submodule_change:
            submodule_issue = re.search(ISSUE_ID_PATTERN, line)
            if submodule_issue is not None:
                if submodule_change is not None:
                    issue_id = submodule_issue.group(1)
                    submodule_change.issues.add(issue_id)
            else:
                task = update_task(line, commit, task)
        elif task == Task.finish_commit:
            commit_issue = re.search(ISSUE_ID_PATTERN, line)
            if commit_issue is not None:
                commit.issues.add(commit_issue.group(1))
            else:
                commit_end = re.match(CHANGE_ID_PATTERN, line)
                if commit_end is not None:
                    commit = finish(commit, commits, release)
                    task = Task.start_commit
        else:
            raise RuntimeError("FIXME")
    return commits, submodules


def change(submodule_subject, submodules, commit):
    submodule_change = Change()
    submodule_change.subject = submodule_subject.group(1)
    for exclusion in EXCLUDED_SUBJECTS:
        if exclusion in submodule_change.subject:
            return None
    for noted_change in submodules[commit.submodule]:
        if noted_change.subject == submodule_change.subject:
            return noted_change
    submodule_change.issues = set()
    submodules[commit.submodule].append(submodule_change)
    return submodule_change


def update_task(line, commit, task):
    update_end = re.search(COMMIT_SHA1_PATTERN, line)
    if update_end is not None:
        task = commit.reset(update_end, task)
    return task


def finish(commit, commits, release):
    if len(commit.issues) == 0:
        for exclusion in EXCLUDED_SUBJECTS:
            if exclusion in commit.subject:
                return Commit()
        for noted_commit in commits:
            if noted_commit.subject == commit.subject:
                return Commit()
    if newly_released(commit.sha1, release):
        commits.append(commit)
    else:
        print(f"Previously released: commit {commit.sha1}")
    return Commit()


def print_commits(commits, md):
    md.write("\n## Core Changes\n")
    for commit in commits:
        md.write(f"\n* {commit.subject}\n")
        for issue in sorted(commit.issues):
            md.write(f"  [Issue {issue}]({ISSUE_URL}{issue})\n")


def print_submodules(submodules, md):
    md.write("\n## Plugin Changes\n")
    for submodule in sorted(submodules):
        plugin = re.search(PLUGIN_PATTERN, submodule)
        md.write(f"\n### {plugin.group(1)}\n")
        for submodule_change in submodules[submodule]:
            md.write(f"\n* {submodule_change.subject}\n")
            for issue in sorted(submodule_change.issues):
                md.write(f"  [Issue {issue}]({ISSUE_URL}{issue})\n")


def print_notes(commits, submodules):
    with open("release_noter.md", "w") as md:
        md.write("# Release Notes\n")
        print_submodules(submodules, md)
        print_commits(commits, md)


if __name__ == "__main__":
    script_options = parse_args()
    release_tag = check_args(script_options)
    change_log = open_git_log(script_options)
    core_changes, submodule_changes = parse_log(change_log, release_tag)
    print_notes(core_changes, submodule_changes)
