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

package com.google.gerrit.server.index.project;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.server.index.IndexRewriter;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.project.ProjectData;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class ProjectIndexRewriter implements IndexRewriter<ProjectData> {
  private final ProjectIndexCollection indexes;

  @Inject
  ProjectIndexRewriter(ProjectIndexCollection indexes) {
    this.indexes = indexes;
  }

  @Override
  public Predicate<ProjectData> rewrite(Predicate<ProjectData> in, QueryOptions opts)
      throws QueryParseException {
    ProjectIndex index = indexes.getSearchIndex();
    checkNotNull(index, "no active search index configured for projects");
    return new IndexedProjectQuery(index, in, opts);
  }
}
