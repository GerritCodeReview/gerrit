// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.query.project;

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.index.project.ProjectField;
import com.google.gerrit.index.project.ProjectPredicate;
import com.google.gerrit.index.query.Predicate;
import java.util.Locale;

/** Utility class to create predicates for project index queries. */
public class ProjectPredicates {
  public static Predicate<ProjectData> name(Project.NameKey nameKey) {
    return new ProjectPredicate(ProjectField.NAME_SPEC, nameKey.get());
  }

  public static Predicate<ProjectData> parent(Project.NameKey parentNameKey) {
    return new ProjectPredicate(ProjectField.PARENT_NAME_SPEC, parentNameKey.get());
  }

  public static Predicate<ProjectData> inname(String name) {
    return new ProjectPredicate(ProjectField.NAME_PART_SPEC, name.toLowerCase(Locale.US));
  }

  public static Predicate<ProjectData> description(String description) {
    return new ProjectPredicate(ProjectField.DESCRIPTION_SPEC, description);
  }

  public static Predicate<ProjectData> state(ProjectState state) {
    return new ProjectPredicate(ProjectField.STATE_SPEC, state.name());
  }

  private ProjectPredicates() {}
}
