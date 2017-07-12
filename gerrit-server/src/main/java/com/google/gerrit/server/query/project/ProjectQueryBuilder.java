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

import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryBuilder;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;

/** Parses a query string meant to be applied to project objects. */
public class ProjectQueryBuilder extends QueryBuilder<ProjectState> {
  private static final QueryBuilder.Definition<ProjectState, ProjectQueryBuilder> mydef =
      new QueryBuilder.Definition<>(ProjectQueryBuilder.class);

  @Inject
  ProjectQueryBuilder() {
    super(mydef);
  }

  @Operator
  public Predicate<ProjectState> name(String name) {
    return ProjectPredicates.name(new Project.NameKey(name));
  }
}
