#!/usr/bin/env python

import argparse
import hashlib
import re
import subprocess

from enum import Enum

EXCLUDED_SUBJECTS = set(
    [
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
        "nam",  # naming, rename, renaming
        "nits",
        "nongoogle",
        "prone",  # error prone &co.
        "register",
        "Register",
        "remove",
        "Remove",
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
    ]
)

COMMIT_SHA1_PATTERN = r"^commit ([a-z0-9]+)$"
DATE_HEADER_PATTERN = r"Date: .+"
SUBJECT_SUBMODULES_PATTERN = r"^Update git submodules$"
UPDATE_SUBMODULE_PATTERN = r"\* Update ([a-z/\-]+) from branch '.+'"
SUBMODULE_SUBJECT_PATTERN = r"^- (.+)"
SUBMODULE_MERGE_PATTERN = r".+Merge .+"
ISSUE_ID_PATTERN = r"[a-zA-Z]+: [Ii]ssue ([0-9]+)"
CHANGE_ID_PATTERN = r"^Change-Id: [I0-9a-z]+$"
PLUGIN_PATTERN = r"plugins/([a-z\-]+)"
RELEASE_TAG_PATTERN = r"v[0-9]+\.[0-9]+\.[0-9]+$"

ISSUE_URL = "https://bugs.chromium.org/p/gerrit/issues/detail?id="
GIT_COMMAND = "git"
UTF8 = "UTF-8"


def parse_args():
    parser = argparse.ArgumentParser(
        description="Generate an initial release notes markdown file.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "-c",
        "--previous-check",
        dest="check",
        required=False,
        default=False,
        action="store_true",
        help="check commits for previous releases; n-square (beta)",
    )
    parser.add_argument(
        "-p", "--previous-tag", dest="previous", required=True, help="previous tag"
    )
    parser.add_argument(
        "-r", "--release-tag", dest="release", required=True, help="release tag"
    )
    return parser.parse_args()


def newly_released(options, commit_sha1):
    if not options.check:
        return True
    git_tag = [
        GIT_COMMAND,
        "tag",
        "--contains",
        commit_sha1,
    ]
    process = subprocess.Popen(git_tag, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    verdict = True
    for line in iter(process.stdout.readline, ""):
        if process.poll() is not None:
            break
        line = line.strip().decode(UTF8)
        if not re.match(rf"{re.escape(options.release)}$", line):
            # Wrongfully pushed or malformed tags ignored.
            # Preceeding release-candidate (-rcN) tags treated as newly released.
            verdict = not re.match(RELEASE_TAG_PATTERN, line)
    return verdict


def open_git_log(options):
    git_log = [
        GIT_COMMAND,
        "log",
        "--no-merges",
        "{}..{}".format(options.previous, options.release),
    ]
    return subprocess.Popen(git_log, stdout=subprocess.PIPE)


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


def parse_log(options, process):
    commit = Commit()
    commits = []
    submodules = dict()
    submodule_change = None
    task = Task.start_commit
    for line in iter(process.stdout.readline, ""):
        if process.poll() is not None:
            break
        line = line.strip().decode(UTF8)
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
                    commit = finish(options, commit, commits)
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


def finish(options, commit, commits):
    if len(commit.issues) == 0:
        for exclusion in EXCLUDED_SUBJECTS:
            if exclusion in commit.subject:
                return Commit()
        for noted_commit in commits:
            if noted_commit.subject == commit.subject:
                return Commit()
    if newly_released(options, commit.sha1):
        commits.append(commit)
    else:
        print("Previously released commit {}".format(commit.sha1))
    return Commit()


def print_commits(commits, md):
    md.write("\n## Core Changes\n")
    for commit in commits:
        md.write("\n* {}\n".format(commit.subject))
        for issue in sorted(commit.issues):
            md.write("  [Issue {}]({}{})\n".format(issue, ISSUE_URL, issue))


def print_submodules(submodules, md):
    md.write("\n## Plugin Changes\n")
    for submodule in sorted(submodules):
        plugin = re.search(PLUGIN_PATTERN, submodule)
        md.write("\n### {}\n".format(plugin.group(1)))
        for submodule_change in submodules[submodule]:
            md.write("\n* {}\n".format(submodule_change.subject))
            for issue in sorted(submodule_change.issues):
                md.write("  [Issue {}]({}{})\n".format(issue, ISSUE_URL, issue))


def print_notes(commits, submodules):
    md = open("release_noter.md", "w")
    md.write("# Release Notes\n")
    print_submodules(submodules, md)
    print_commits(commits, md)
    md.close()


if __name__ == "__main__":
    options = parse_args()
    process = open_git_log(options)
    commits, submodules = parse_log(options, process)
    print_notes(commits, submodules)
