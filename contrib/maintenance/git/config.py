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
    def __init__(self, config_path, cmd_options):
        self.path = config_path
        self.cmd_options = cmd_options
        self.contents = {}

    def __enter__(self):
        LOG.debug("reader")
        self.file = open(self.path, "r", encoding="utf-8")
        self._parse()
        self._parse_cmd_options()
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

    def list(self):
        return self.contents

    def _parse(self):
        current_section = None
        current_subsection = None
        for line in self.file.readlines():
            LOG.debug("Read config line: %s", line)
            line = line.split("#", 1)[0].strip()
            if not line:
                continue
            LOG.debug("Not a comment: |%s|", line)
            section_match = SECTION_HEADER_PATTERN.match(line)
            if section_match:
                LOG.debug(section_match.groupdict())
                current_section = section_match.group("section").lower()
                if not current_section:
                    raise GitConfigException("Section has to be set with subsection.")
                LOG.debug("Parsed section %s", current_section)
                current_subsection = section_match.group("subsection")
                if not current_subsection:
                    current_subsection = DEFAULT_SUBSECTION
                current_subsection = current_subsection.lower()
                LOG.debug("Parsed subsection %s", current_subsection)
            else:
                LOG.debug("Parsing key-value pair: |%s|", line)
                key, value = line.split("=", 1)
                key = key.strip().lower()
                value = value.strip()
                if value.lower() == "true":
                    value = True
                elif value.lower() == "false":
                    value = False
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

    def _parse_cmd_options(self):
        for option in self.cmd_options:
            key, value = option.split("=", 1)
            key_parts = key.split(".")
            if len(key_parts) == 2:
                section = key_parts[0].lower()
                subsection = DEFAULT_SUBSECTION
                key = key_parts[1].lower()
            elif len(key_parts) == 3:
                section = key_parts[0].lower()
                subsection = key_parts[1].lower()
                key = key_parts[2].lower()
            else:
                raise GitConfigException(f"Invalid git config option: {option}")
            self._ensure_full_section(section, subsection)
            self.contents[section][subsection][key] = value

    def _ensure_full_section(self, section, subsection):
        if section not in self.contents:
            self.contents[section] = {}
        if subsection not in self.contents[section]:
            self.contents[section][subsection] = {}


class GitConfigWriter(GitConfigReader):
    def __init__(self, config_path):
        super().__init__(config_path, [])

    def __enter__(self):
        self.file = open(self.path, "r+", encoding="utf-8")
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
        if key not in self.contents[section][subsection]:
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
        if not self.contents[section][subsection]:
            self.contents[section].pop(subsection, None)
        if not self.contents[section]:
            self.contents.pop(section, None)

    def remove(self, section, subsection, key, value):
        section, subsection, key = self._ensure_full_key_format(
            section, subsection, key
        )
        if (
            section not in self.contents
            or subsection not in self.contents[section]
            or key not in self.contents[section][subsection]
        ):
            return
        try:
            self.contents[section][subsection][key].remove(value)
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
        self.file.truncate(0)
        self.file.seek(0)
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
