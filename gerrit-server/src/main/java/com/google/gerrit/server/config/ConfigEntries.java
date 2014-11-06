// Copyright (C) 2014 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.config;

import org.eclipse.jgit.lib.Config;

import java.util.HashSet;
import java.util.Set;

public class ConfigEntries {
  public String name;
  public Set<Section> sections;

  public ConfigEntries(String name) {
    this.name = name;
    this.sections = new HashSet<>();
  }

  public static ConfigEntries fromConfig(Config cfg, String fileName) {
    ConfigEntries cfgEntries = new ConfigEntries(fileName);
    for (String section : cfg.getSections()) {
      for (String subSection : cfg.getSubsections(section)) {
        for (String key : cfg.getNames(section, subSection)) {
          for (String value : cfg.getStringList(section, subSection, key)) {
            cfgEntries.addValue(section, subSection, key, value);
          }
        }
      }
    }
    return cfgEntries;
  }

  public Section getSection(String sectionName) {
    for (Section s : sections) {
      if (s.name == sectionName) {
        return s;
      }
    }
    Section section = new Section(sectionName);
    sections.add(section);
    return section;
  }

  public SubSection getSubSection(String sectionName, String subSectionName) {
    return getSection(sectionName).get(subSectionName);
  }

  public Key getKey(String sectionName, String subSectionName, String keyName) {
    return getSection(sectionName).get(subSectionName).get(keyName);
  }

  public void addValue(String sectionName, String subSectionName,
      String keyName, String value) {
    getSection(sectionName).get(subSectionName).get(keyName).add(value);
  }
}
