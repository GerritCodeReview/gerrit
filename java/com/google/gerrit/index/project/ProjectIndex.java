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

package com.google.gerrit.index.project;

import com.google.gerrit.entities.Project;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexDefinition;
import com.google.gerrit.index.query.Predicate;
import java.util.function.Function;

/**
 * Index for Gerrit projects (repositories). This class is mainly used for typing the generic parent
 * class that contains actual implementations.
 */
public interface ProjectIndex extends Index<Project.NameKey, ProjectData> {

  public interface Factory
      extends IndexDefinition.IndexFactory<Project.NameKey, ProjectData, ProjectIndex> {}

  @Override
  default Predicate<ProjectData> keyPredicate(Project.NameKey nameKey) {
    return new ProjectPredicate(ProjectField.NAME_SPEC, nameKey.get());
  }

  Function<ProjectData, Project.NameKey> ENTITY_TO_KEY = (p) -> p.getProject().getNameKey();
}
