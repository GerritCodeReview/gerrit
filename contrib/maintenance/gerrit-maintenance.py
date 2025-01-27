#!/usr/bin/python3

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

import argparse
import logging
import sys

import cli.gc

from gerrit.site import Site
from gerrit.tasks.gc import BatchGitGarbageCollection
from git.gc import GitGarbageCollectionProvider

logging.basicConfig(
    level=logging.INFO,
    stream=sys.stdout,
    format="%(asctime)s [%(levelname)s] %(message)s",
)


def _run_projects_gc(args):
    site = Site(args[0].site)
    projects = (
        args[0].projects
        if args[0].projects
        else site.get_projects(args[0].skip_projects)
    )
    BatchGitGarbageCollection(
        site,
        projects,
        GitGarbageCollectionProvider.get(args[0].pack_refs, args[0].config),
    ).run(args[1])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "-d",
        "--site",
        help="Path to Gerrit site",
        dest="site",
        action="store",
        default="/var/gerrit",
    )
    parser.set_defaults(func=lambda x: parser.print_usage())

    subparsers = parser.add_subparsers()

    parser_projects = subparsers.add_parser(
        "projects",
        help="Tools for working with Gerrit projects.",
    )
    parser_projects.add_argument(
        "-p",
        "--project",
        help=(
            "Which project to gc. Can be used multiple times. If not given, all "
            "attrs=projects (except for `--skipped` ones) will be gc'ed."
        ),
        dest="projects",
        action="append",
        default=[],
    )
    parser_projects.add_argument(
        "-s",
        "--skip",
        help="Which project to skip. Can be used multiple times.",
        dest="skip_projects",
        action="append",
        default=[],
    )
    parser_projects.set_defaults(func=lambda x: parser_projects.print_usage())

    subparsers_projects = parser_projects.add_subparsers()
    parser_projects_gc = subparsers_projects.add_parser(
        "gc",
        prog=cli.gc.PROG,
        description=cli.gc.DESCRIPTION,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    cli.gc.add_arguments(parser_projects_gc)
    parser_projects_gc.add_argument(
        "-c",
        "--config",
        help="Git config options to apply.",
        dest="config",
        action="append",
        default=[],
    )
    parser_projects_gc.set_defaults(func=_run_projects_gc)

    args = parser.parse_known_args()
    args[0].func(args)


if __name__ == "__main__":
    main()
