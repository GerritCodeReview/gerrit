// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import com.google.gerrit.entities.Project;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.index.project.ProjectIndex;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.Predicate;

/**
 * This class wraps an index and assumes the search index can't handle any queries. However, it does
 * return the current schema as the assumption is that we need a search index for starting Gerrit in
 * the first place and only later lose the index connection (making it so that we can't send
 * requests there anymore).
 */
public class DisabledProjectIndex implements ProjectIndex {
  private final ProjectIndex index;

  public DisabledProjectIndex(ProjectIndex index) {
    this.index = index;
  }

  public ProjectIndex unwrap() {
    return index;
  }

  @Override
  public Schema<ProjectData> getSchema() {
    return index.getSchema();
  }

  @Override
  public void close() {
    index.close();
  }

  @Override
  public void replace(ProjectData obj) {
    throw new UnsupportedOperationException("ProjectIndex is disabled");
  }

  @Override
  public void delete(Project.NameKey key) {
    throw new UnsupportedOperationException("ProjectIndex is disabled");
  }

  @Override
  public void deleteAll() {
    throw new UnsupportedOperationException("ProjectIndex is disabled");
  }

  @Override
  public DataSource<ProjectData> getSource(Predicate<ProjectData> p, QueryOptions opts) {
    throw new UnsupportedOperationException("ProjectIndex is disabled");
  }

  @Override
  public void markReady(boolean ready) {
    throw new UnsupportedOperationException("ProjectIndex is disabled");
  }
}
