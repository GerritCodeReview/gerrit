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

from git.gc import MAX_LOOSE_REF_COUNT


PROG = "Execute Git Garbage Collection."
DESCRIPTION = """
Run Git GC with additional cleanup steps on repository.

To specify a one-time --aggressive git gc for a repository X, simply
create an empty file called 'gc-aggressive-once' in the `/path/to/X.git`
folder:

    $ cd /path/to/X.git
    $ touch gc-aggressive-once

On the next run, gc.sh will use --aggressive option for gc-ing this
repository *and* will remove this file. Next time, gc.sh again runs
normal gc for this repository.

To specify a permanent --aggressive git gc for a repository, create
an empty file named "gc-aggressive" in the same folder:

    $ cd /path/to/X.git
    $ touch gc-aggressive

Every next git gc on this repository will use --aggressive option.
"""


def add_arguments(parser):
    parser.add_argument(
        "-r",
        "--pack-all-refs",
        help=(
            "Whether to pack all refs, "
            f"if more than {MAX_LOOSE_REF_COUNT} loose refs exist."
        ),
        dest="pack_refs",
        action="store_true",
    )
