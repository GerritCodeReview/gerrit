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

import java.util.HashSet;
import java.util.Set;

public class Section {
  public String name;
  public Set<SubSection> subSections;

  public Section(String name) {
    this.name = name;
    this.subSections = new HashSet<>();
  }

  public SubSection get(String subSectionName) {
    for (SubSection s : subSections) {
      if (s.name == subSectionName) {
        return s;
      }
    }
    SubSection subSection = new SubSection(subSectionName);
    subSections.add(subSection);
    return subSection;
  }
}
