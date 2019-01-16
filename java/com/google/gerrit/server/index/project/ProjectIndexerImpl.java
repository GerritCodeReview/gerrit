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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.events.ProjectIndexedListener;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.index.project.ProjectIndex;
import com.google.gerrit.index.project.ProjectIndexCollection;
import com.google.gerrit.index.project.ProjectIndexer;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.util.Collection;
import java.util.Collections;

public class ProjectIndexerImpl implements ProjectIndexer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    ProjectIndexerImpl create(ProjectIndexCollection indexes);

    ProjectIndexerImpl create(@Nullable ProjectIndex index);
  }

  private final ProjectCache projectCache;
  private final PluginSetContext<ProjectIndexedListener> indexedListener;
  @Nullable private final ProjectIndexCollection indexes;
  @Nullable private final ProjectIndex index;

  @AssistedInject
  ProjectIndexerImpl(
      ProjectCache projectCache,
      PluginSetContext<ProjectIndexedListener> indexedListener,
      @Assisted ProjectIndexCollection indexes) {
    this.projectCache = projectCache;
    this.indexedListener = indexedListener;
    this.indexes = indexes;
    this.index = null;
  }

  @AssistedInject
  ProjectIndexerImpl(
      ProjectCache projectCache,
      PluginSetContext<ProjectIndexedListener> indexedListener,
      @Assisted @Nullable ProjectIndex index) {
    this.projectCache = projectCache;
    this.indexedListener = indexedListener;
    this.indexes = null;
    this.index = index;
  }

  @Override
  public void index(Project.NameKey nameKey) {
    ProjectState projectState = projectCache.get(nameKey);
    if (projectState != null) {
      logger.atFine().log("Replace project %s in index", nameKey.get());
      ProjectData projectData = projectState.toProjectData();
      for (ProjectIndex i : getWriteIndexes()) {
        try (TraceTimer traceTimer =
            TraceContext.newTimer(
                "Replacing project %s in index version %d",
                nameKey.get(), i.getSchema().getVersion())) {
          i.replace(projectData);
        }
      }
      fireProjectIndexedEvent(nameKey.get());
    } else {
      logger.atFine().log("Delete project %s from index", nameKey.get());
      for (ProjectIndex i : getWriteIndexes()) {
        try (TraceTimer traceTimer =
            TraceContext.newTimer(
                "Deleting project %s in index version %d",
                nameKey.get(), i.getSchema().getVersion())) {
          i.delete(nameKey);
        }
      }
    }
  }

  private void fireProjectIndexedEvent(String name) {
    indexedListener.runEach(l -> l.onProjectIndexed(name));
  }

  private Collection<ProjectIndex> getWriteIndexes() {
    if (indexes != null) {
      return indexes.getWriteIndexes();
    }

    return index != null ? Collections.singleton(index) : ImmutableSet.of();
  }
}
