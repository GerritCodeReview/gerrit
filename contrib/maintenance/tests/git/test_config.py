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
import pytest

from git.config import DEFAULT_SUBSECTION, GitConfigReader, GitConfigWriter

CONFIG = """
[section]
  key = value
[section "subsection"]
  other_key = test
  other_key = another_value
  another_key = test
[another_section]
  # some comment
  gerrit = awesome # of course
  boolean = true
  another_boolean = false
"""

CONFIG_DICT = {
    "section": {
        "default": {"key": "value"},
        "subsection": {"other_key": ["test", "another_value"], "another_key": "test"},
    },
    "another_section": {
        "default": {"gerrit": "awesome", "boolean": True, "another_boolean": False}
    },
}


@pytest.fixture(scope="function")
def repo_with_config(repo):
    with open(os.path.join(repo, "config"), "w") as f:
        f.write(CONFIG)
    return repo


def test_list_config(repo_with_config):
    with GitConfigReader(os.path.join(repo_with_config, "config"), []) as reader:
        assert reader.list() == CONFIG_DICT


def test_get_config(repo_with_config):
    with GitConfigReader(os.path.join(repo_with_config, "config"), []) as reader:
        assert (
            reader.get("section", None, "key")
            == CONFIG_DICT["section"]["default"]["key"]
        )
        assert (
            reader.get("section", "subsection", "another_key")
            == CONFIG_DICT["section"]["subsection"]["another_key"]
        )
        assert (
            reader.get("section", "subsection", "other_key")
            == CONFIG_DICT["section"]["subsection"]["other_key"][-1]
        )
        assert (
            reader.get("section", "subsection", "other_key", all=True)
            == CONFIG_DICT["section"]["subsection"]["other_key"]
        )
        assert reader.get("another_section", "default", "boolean")
        assert not reader.get("another_section", "default", "another_boolean")


def test_get_config_with_override(repo_with_config):
    with GitConfigReader(
        os.path.join(repo_with_config, "config"), ["section.key=override"]
    ) as reader:
        assert reader.get("section", None, "key") == "override"
        assert (
            reader.get("section", "subsection", "another_key")
            == CONFIG_DICT["section"]["subsection"]["another_key"]
        )


def test_set_config(repo_with_config):
    with GitConfigWriter(os.path.join(repo_with_config, "config")) as writer:
        writer.set("new", None, "key", "value")
        writer.set("new", "new_sub", "key", "val")
        writer.write()

    with GitConfigReader(os.path.join(repo_with_config, "config"), []) as reader:
        assert reader.get("new", None, "key") == "value"
        assert reader.get("new", "new_sub", "key") == "val"


def test_add_to_config(repo_with_config):
    with GitConfigWriter(os.path.join(repo_with_config, "config")) as writer:
        writer.add("new", None, "key", "value")
        writer.add("section", None, "key", "value2")
        writer.add("section", "subsection", "other_key", "val")
        writer.write()

    with GitConfigReader(os.path.join(repo_with_config, "config"), []) as reader:
        assert reader.get("new", None, "key") == "value"
        assert reader.get("section", None, "key") == "value2"
        assert reader.get("section", "subsection", "other_key") == "val"


def test_unset_config(repo_with_config):
    with GitConfigWriter(os.path.join(repo_with_config, "config")) as writer:
        writer.unset("section", None, "key")
        writer.unset("section", "subsection", "another_key")
        writer.write()

    with GitConfigReader(os.path.join(repo_with_config, "config"), []) as reader:
        config = reader.list()
        assert DEFAULT_SUBSECTION not in config["section"]
        assert "another_key" not in config["section"]["subsection"]


def test_remove_config(repo_with_config):
    with GitConfigWriter(os.path.join(repo_with_config, "config")) as writer:
        writer.remove("section", "subsection", "other_key", "test")
        writer.write()

    with GitConfigReader(os.path.join(repo_with_config, "config"), []) as reader:
        config = reader.list()
        assert "test" not in config["section"]["subsection"]["other_key"]
