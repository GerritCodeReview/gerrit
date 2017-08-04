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

import com.google.common.primitives.Ints;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectData;
import com.google.gerrit.server.query.LimitPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryBuilder;
import com.google.gerrit.server.query.QueryParseException;
import com.google.inject.Inject;

/** Parses a query string meant to be applied to project objects. */
public class ProjectQueryBuilder extends QueryBuilder<ProjectData> {
  public static final String FIELD_LIMIT = "limit";

  private static final QueryBuilder.Definition<ProjectData, ProjectQueryBuilder> mydef =
      new QueryBuilder.Definition<>(ProjectQueryBuilder.class);

  @Inject
  ProjectQueryBuilder() {
    super(mydef);
  }

  @Operator
  public Predicate<ProjectData> name(String name) {
    return ProjectPredicates.name(new Project.NameKey(name));
  }

  @Operator
  public Predicate<ProjectData> limit(String query) throws QueryParseException {
    Integer limit = Ints.tryParse(query);
    if (limit == null) {
      throw error("Invalid limit: " + query);
    }
    return new LimitPredicate<>(FIELD_LIMIT, limit);
  }
}
