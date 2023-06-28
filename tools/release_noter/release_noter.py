#!/usr/bin/env python3

import argparse
import os
import re
import subprocess

from enum import Enum
from jinja2 import Template
from os import path
from pygerrit2 import Anonymous, GerritRestAPI

EXCLUDED_SUBJECTS = {
    "annotat",
    "assert",
    "AutoValue",
    "avadoc",  # Javadoc &co.
    "avaDoc",
    "ava-doc",
    "baz",  # bazel, bazlet(s)
    "Baz",
    "circular",
    "class",
    "common.ts",
    "construct",
    "controls",
    "debounce",
    "Debounce",
    "decorat",
    "efactor",  # Refactor &co.
    "format",
    "Format",
    "getter",
    "gr-",
    "hide",
    "icon",
    "ignore",
    "immutab",
    "import",
    "inject",
    "iterat",
    "IT",
    "js",
    "label",
    "licence",
    "license",
    "lint",
    "listener",
    "Listener",
    "lock",
    "method",
    "metric",
    "mock",
    "module",
    "naming",
    "nits",
    "nongoogle",
    "prone",  # error prone &co.
    "Prone",
    "register",
    "Register",
    "remove",
    "Remove",
    "rename",
    "Rename",
    "Revert",
    "serializ",
    "Serializ",
    "server.go",
    "setter",
    "spell",
    "Spell",
    "test",  # testing, tests; unit or else
    "Test",
    "thread",
    "tsetse",
    "type",
    "Type",
    "typo",
    "util",
    "variable",
    "version",
    "warning",
}

COMMIT_SHA1_PATTERN = r"^commit ([a-z0-9]+)$"
DATE_HEADER_PATTERN = r"Date: .+"
SUBJECT_SUBMODULES_PATTERN = r"^Update git submodules$"
ISSUE_ID_PATTERN = r"[a-zA-Z]+: [Ii]ssue ([0-9]+)"
CHANGE_ID_PATTERN = r"^Change-Id: [I0-9a-z]+$"
PLUGIN_PATTERN = r"plugins/([a-z\-]+)"
RELEASE_VERSIONS_PATTERN = r"v([0-9\.\-rc]+)\.\.v([0-9\.\-rc]+)"
RELEASE_MAJOR_PATTERN = r"^([0-9]+\.[0-9]+).+"
RELEASE_DOC_PATTERN = r"^([0-9]+\.[0-9]+\.[0-9]+).*"

CHANGE_URL = "/c/gerrit/+/"
COMMIT_URL = "/changes/?q=commit%3A"
GERRIT_URL = "https://gerrit-review.googlesource.com"
ISSUE_URL_MONORAIL = "https://bugs.chromium.org/p/gerrit/issues/detail?id="
ISSUE_URL_TRACKER = "https://issues.gerritcodereview.com/issues/"

MARKDOWN = "release_noter"
GIT_COMMAND = "git"
GIT_PATH = "../.."
PLUGINS = "plugins/"
UTF8 = "UTF-8"


def parse_args():
    parser = argparse.ArgumentParser(
        description="Generate an initial release notes markdown file.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "-l",
        "--link",
        dest="link",
        required=False,
        default=False,
        action="store_true",
        help="link commits to change in Gerrit; slower as it gets each _number from gerrit",
    )
    parser.add_argument("range", help="git log revision range")
    return parser.parse_args()


def list_submodules():
    submodule_names = [
        GIT_COMMAND,
        "submodule",
        "foreach",
        "--quiet",
        "echo $name",
    ]
    return subprocess.check_output(submodule_names, cwd=f"{GIT_PATH}", encoding=UTF8)


def open_git_log(options, cwd=os.getcwd()):
    git_log = [
        GIT_COMMAND,
        "log",
        "--no-merges",
        options.range,
    ]
    return subprocess.check_output(git_log, cwd=cwd, encoding=UTF8)


class Component:
    name = None
    sentinels = set()

    def __init__(self, name, sentinels):
        self.name = name
        self.sentinels = sentinels


class Components(Enum):
    plugin_ce = Component("Codemirror-editor", {PLUGINS})
    plugin_cm = Component("Commit-message-length-validator", {PLUGINS})
    plugin_dp = Component("Delete-project", {PLUGINS})
    plugin_dc = Component("Download-commands", {PLUGINS})
    plugin_gt = Component("Gitiles", {PLUGINS})
    plugin_ho = Component("Hooks", {PLUGINS})
    plugin_pm = Component("Plugin-manager", {PLUGINS})
    plugin_re = Component("Replication", {PLUGINS})
    plugin_rn = Component("Reviewnotes", {PLUGINS})
    plugin_su = Component("Singleusergroup", {PLUGINS})
    plugin_wh = Component("Webhooks", {PLUGINS})

    ui = Component(
        "Polygerrit UI",
        {"poly", "gwt", "button", "dialog", "icon", "hover", "menu", "ux"},
    )
    doc = Component("Documentation", {"document"})
    jgit = Component("JGit", {"jgit"})
    deps = Component("Other dependency", {"upgrade", "dependenc"})
    otherwise = Component("Other core", {})


class Task(Enum):
    start_commit = 1
    finish_headers = 2
    capture_subject = 3
    finish_commit = 4


class Commit:
    sha1 = None
    subject = None
    component = None
    issues = set()

    def reset(self, signature, task):
        if signature is not None:
            self.sha1 = signature.group(1)
            self.subject = None
            self.component = None
            self.issues = set()
            return Task.finish_headers
        return task


def parse_log(process, gerrit, options, commits, cwd=os.getcwd()):
    commit = Commit()
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
            commit.subject = line
            task = Task.finish_commit
        elif task == Task.finish_commit:
            commit_issue = re.search(ISSUE_ID_PATTERN, line)
            if commit_issue is not None:
                commit.issues.add(commit_issue.group(1))
            else:
                commit_end = re.match(CHANGE_ID_PATTERN, line)
                if commit_end is not None:
                    commit = finish(commit, commits, gerrit, options, cwd)
                    task = Task.start_commit
        else:
            raise RuntimeError("FIXME")


def finish(commit, commits, gerrit, options, cwd):
    if re.match(SUBJECT_SUBMODULES_PATTERN, commit.subject):
        return Commit()
    if len(commit.issues) == 0:
        for exclusion in EXCLUDED_SUBJECTS:
            if exclusion in commit.subject:
                return Commit()
        for component in commits:
            for noted_commit in commits[component]:
                if noted_commit.subject == commit.subject:
                    return Commit()
    set_component(commit, commits, cwd)
    link_subject(commit, gerrit, options, cwd)
    escape_these(commit)
    return Commit()


def set_component(commit, commits, cwd):
    component_found = None
    for component in Components:
        for sentinel in component.value.sentinels:
            if component_found is None:
                if re.match(f"{GIT_PATH}/{PLUGINS}{component.value.name.lower()}", cwd):
                    component_found = component
                elif sentinel.lower() in commit.subject.lower():
                    component_found = component
                if component_found is not None:
                    commits[component].append(commit)
    if component_found is None:
        commits[Components.otherwise].append(commit)
    commit.component = component_found


def init_components():
    components = dict()
    for component in Components:
        components[component] = []
    return components


def link_subject(commit, gerrit, options, cwd):
    if options.link:
        gerrit_change = gerrit.get(f"{COMMIT_URL}{commit.sha1}")
        if not gerrit_change:
            return
        change_number = gerrit_change[0]["_number"]
        plugin_wd = re.search(f"{GIT_PATH}/({PLUGINS}.+)", cwd)
        if plugin_wd is not None:
            change_address = f"{GERRIT_URL}/c/{plugin_wd.group(1)}/+/{change_number}"
        else:
            change_address = f"{GERRIT_URL}{CHANGE_URL}{change_number}"
        short_sha1 = commit.sha1[0:7]
        commit.subject = f"[{short_sha1}]({change_address})\n  {commit.subject}"


def escape_these(in_change):
    in_change.subject = in_change.subject.replace("<", "\\<")
    in_change.subject = in_change.subject.replace(">", "\\>")


def print_commits(commits, md):
    for component in commits:
        if len(commits[component]) > 0:
            if PLUGINS in component.value.sentinels:
                md.write(f"\n### {component.value.name}\n")
            else:
                md.write(f"\n## {component.value.name} changes\n")
            for commit in commits[component]:
                print_from(commit, md)


def print_from(this_change, md):
    md.write("\n*")
    for issue in sorted(this_change.issues):
      if len(issue) > 5:
        md.write(f" [Issue {issue}]({ISSUE_URL_TRACKER}{issue});\n ")
      else:
        md.write(f" [Issue {issue}]({ISSUE_URL_MONORAIL}{issue});\n ")
    md.write(f" {this_change.subject}\n")


def print_template(md, options):
    previous = "0.0.0"
    new = "0.1.0"
    versions = re.search(RELEASE_VERSIONS_PATTERN, options.range)
    if versions is not None:
        previous = versions.group(1)
        new = versions.group(2)
    data = {
        "previous": previous,
        "new": new,
        "major": re.search(RELEASE_MAJOR_PATTERN, new).group(1),
        "doc": re.search(RELEASE_DOC_PATTERN, new).group(1),
    }
    template = Template(open(f"{MARKDOWN}.md.template").read())
    md.write(f"{template.render(data=data)}\n")


def print_notes(commits, options):
    markdown = f"{MARKDOWN}.md"
    next_md = 2
    while path.exists(markdown):
        markdown = f"{MARKDOWN}-{next_md}.md"
        next_md += 1
    with open(markdown, "w") as md:
        print_template(md, options)
        print_commits(commits, md)
        md.write("\n## Bugfix releases\n")


def plugin_changes():
    plugin_commits = init_components()
    for submodule_name in list_submodules().splitlines():
        plugin_name = re.search(PLUGIN_PATTERN, submodule_name)
        if plugin_name is not None:
            plugin_wd = f"{GIT_PATH}/{PLUGINS}{plugin_name.group(1)}"
            plugin_log = open_git_log(script_options, plugin_wd)
            parse_log(
                plugin_log,
                gerrit_api,
                script_options,
                plugin_commits,
                plugin_wd,
            )
    return plugin_commits


if __name__ == "__main__":
    gerrit_api = GerritRestAPI(url=GERRIT_URL, auth=Anonymous())
    script_options = parse_args()
    if script_options.link:
        print("Link option used; slower.")
    noted_changes = plugin_changes()
    change_log = open_git_log(script_options)
    parse_log(change_log, gerrit_api, script_options, noted_changes)
    print_notes(noted_changes, script_options)
