// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.CurrentUser;

/**
 * Matches an AccessSection against a reference name.
 *
 * <p>These matchers are "compiled" versions of the AccessSection name, supporting faster selection
 * of which sections are relevant to any given input reference.
 */
public class SectionMatcher extends RefPatternMatcher {
  static SectionMatcher wrap(Project.NameKey project, AccessSection section) {
    String ref = section.getName();
    if (AccessSection.isValidRefSectionName(ref)) {
      return new SectionMatcher(project, section, getMatcher(ref));
    }
    return null;
  }

  private final Project.NameKey project;
  private final AccessSection section;
  private final RefPatternMatcher matcher;

  public SectionMatcher(Project.NameKey project, AccessSection section, RefPatternMatcher matcher) {
    this.project = project;
    this.section = section;
    this.matcher = matcher;
  }

  @Override
  public boolean match(String ref, CurrentUser user) {
    return this.matcher.match(ref, user);
  }

  public AccessSection getSection() {
    return section;
  }

  public RefPatternMatcher getMatcher() {
    return matcher;
  }

  public Project.NameKey getProject() {
    return project;
  }
}
