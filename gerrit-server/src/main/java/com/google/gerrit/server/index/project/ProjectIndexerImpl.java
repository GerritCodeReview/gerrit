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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.events.ProjectIndexedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.index.Index;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectData;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

public class ProjectIndexerImpl implements ProjectIndexer {
  public interface Factory {
    ProjectIndexerImpl create(ProjectIndexCollection indexes);

    ProjectIndexerImpl create(@Nullable ProjectIndex index);
  }

  private final ProjectCache projectCache;
  private final DynamicSet<ProjectIndexedListener> indexedListener;
  private final ProjectIndexCollection indexes;
  private final ProjectIndex index;

  @AssistedInject
  ProjectIndexerImpl(
      ProjectCache projectCache,
      DynamicSet<ProjectIndexedListener> indexedListener,
      @Assisted ProjectIndexCollection indexes) {
    this.projectCache = projectCache;
    this.indexedListener = indexedListener;
    this.indexes = indexes;
    this.index = null;
  }

  @AssistedInject
  ProjectIndexerImpl(
      ProjectCache projectCache,
      DynamicSet<ProjectIndexedListener> indexedListener,
      @Assisted ProjectIndex index) {
    this.projectCache = projectCache;
    this.indexedListener = indexedListener;
    this.indexes = null;
    this.index = index;
  }

  @Override
  public void index(Project.NameKey nameKey) throws IOException {
    for (Index<?, ProjectData> i : getWriteIndexes()) {
      i.replace(projectCache.get(nameKey).toProjectData());
    }
    fireProjectIndexedEvent(nameKey.get());
  }

  private void fireProjectIndexedEvent(String name) {
    for (ProjectIndexedListener listener : indexedListener) {
      listener.onProjectIndexed(name);
    }
  }

  private Collection<ProjectIndex> getWriteIndexes() {
    if (indexes != null) {
      return indexes.getWriteIndexes();
    }

    return index != null ? Collections.singleton(index) : ImmutableSet.of();
  }
}
