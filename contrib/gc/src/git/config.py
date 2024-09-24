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
import re

DEFAULT_SUBSECTION = "default"
SECTION_HEADER_PATTERN = re.compile(
    r"^\[(?P<section>[^\s\"]+)\s?\"?(?P<subsection>[^\s\"]+)?\"?\]$"
)
LOG = logging.getLogger(__name__)


class GitConfigException(Exception):
    """Exception thrown when git config could not be parsed."""


class GitConfigReader:
    def __init__(self, config_path):
        self.path = config_path
        self.file = None
        self.contents = {}

    def __enter__(self):
        self.file = open(self.path, "r")
        self._parse()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.file.close()
        self.contents = {}

    def get(self, section, subsection, key, default=None, all=False):
        if not subsection:
            subsection = DEFAULT_SUBSECTION
        if (
            section not in self.contents
            or subsection not in self.contents[section]
            or key not in self.contents[section][subsection]
        ):
            return default
        value = self.contents[section][subsection][key]
        if isinstance(value, list) and not all:
            return value[-1]
        return value

    def get_boolean(self, section, subsection, key, default=None):
        value = self.get(section, subsection, key, default, False)
        if not value:
            return default
        return value.lower() == "true"

    def list(self):
        return self.contents

    def _parse(self):
        current_section = None
        current_subsection = None
        for line in self.file.readlines():
            LOG.debug("Read config line: %s", line)
            line = line.split("#", 1)[0]
            if not line:
                continue
            section_match = SECTION_HEADER_PATTERN.fullmatch(line.strip())
            if section_match:
                current_section = section_match.group("section").lower()
                if not current_section:
                    raise GitConfigException("Section has to be set with subsection.")
                current_subsection = section_match.group("subsection")
                if not current_subsection:
                    current_subsection = DEFAULT_SUBSECTION
                current_section = current_section.lower()
            else:
                key, value = line.split("=", 1)
                key = key.strip().lower()
                value = value.strip()
                if not current_section:
                    raise GitConfigException(
                        "All key-value pairs have to be part of a section."
                    )
                self._ensure_full_section(current_section, current_subsection)
                if key not in self.contents[current_section][current_subsection]:
                    self.contents[current_section][current_subsection][key] = value
                else:
                    if isinstance(
                        self.contents[current_section][current_subsection][key], list
                    ):
                        self.contents[current_section][current_subsection][key].append(
                            value
                        )
                    else:
                        self.contents[current_section][current_subsection][key] = [
                            self.contents[current_section][current_subsection][key],
                            value,
                        ]
        LOG.debug("Parsed config: %s", self.contents)

    def _ensure_full_section(self, section, subsection):
        if section not in self.contents:
            self.contents[section] = {}
        if subsection not in self.contents[section]:
            self.contents[section][subsection] = {}


class GitConfigWriter(GitConfigReader):
    def __init__(self, config_path):
        super().__init__(config_path)

    def __enter__(self):
        self.file = open(self.path, "w+")
        self._parse()
        return self

    def set(self, section, subsection, key, value):
        section, subsection, key = self._ensure_full_key_format(
            section, subsection, key
        )
        self._ensure_full_section(section, subsection)
        self.contents[section][subsection][key] = value

    def add(self, section, subsection, key, value):
        section, subsection, key = self._ensure_full_key_format(
            section, subsection, key
        )
        self._ensure_full_section(section, subsection)
        if not self.contents[section][subsection][key]:
            self.contents[section][subsection][key] = value
        if isinstance(self.contents[section][subsection][key], list):
            self.contents[section][subsection][key].append(value)
        else:
            self.contents[section][subsection][key] = [
                self.contents[section][subsection][key],
                value,
            ]

    def unset(self, section, subsection, key):
        section, subsection, key = self._ensure_full_key_format(
            section, subsection, key
        )
        if section not in self.contents or subsection not in self.contents[section]:
            return
        self.contents[section][subsection].pop(key, None)

    def remove(self, section, subsection, key):
        section, subsection, key = self._ensure_full_key_format(
            section, subsection, key
        )
        if section not in self.contents or subsection not in self.contents[section]:
            return
        try:
            self.contents[section][subsection][key].remove(key)
        except ValueError:
            return

    def write(self):
        formatted = ""
        for section in self.contents:
            for subsection in self.contents[section]:
                if subsection == DEFAULT_SUBSECTION:
                    formatted += self._format_section(
                        section, None, self.contents[section][subsection]
                    )
                else:
                    formatted += self._format_section(
                        section, subsection, self.contents[section][subsection]
                    )
        LOG.debug("Writing config:\n %s \n\n to %s", formatted, self.file)
        self.file.write(formatted)

    def _format_section(self, section, subsection, entries):
        if subsection:
            formatted = f'[{section} "{subsection}"]\n'
        else:
            formatted = f"[{section}]\n"

        for key, value in entries.items():
            if isinstance(value, list):
                for v in value:
                    formatted += f"  {key} = {v}\n"
            else:
                formatted += f"  {key} = {value}\n"

        return formatted

    def _ensure_full_key_format(self, section, subsection, key):
        if not subsection:
            subsection = DEFAULT_SUBSECTION

        section = section.lower()
        subsection = subsection.lower()
        key = key.lower()

        return section, subsection, key
