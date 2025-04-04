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
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.events.ProjectIndexedListener;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.index.project.ProjectIndex;
import com.google.gerrit.index.project.ProjectIndexCollection;
import com.google.gerrit.index.project.ProjectIndexer;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * Implementation for indexing a Gerrit-managed repository (project). The project will be loaded
 * from {@link ProjectCache}.
 */
public class ProjectIndexerImpl implements ProjectIndexer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    ProjectIndexerImpl create(ProjectIndexCollection indexes);

    ProjectIndexerImpl create(ProjectIndexCollection indexes, boolean notifyListeners);

    ProjectIndexerImpl create(@Nullable ProjectIndex index);
  }

  private final ProjectCache projectCache;
  private final PluginSetContext<ProjectIndexedListener> indexedListener;
  @Nullable private final ProjectIndexCollection indexes;
  @Nullable private final ProjectIndex index;
  private final boolean notifyListeners;

  @AssistedInject
  ProjectIndexerImpl(
      ProjectCache projectCache,
      PluginSetContext<ProjectIndexedListener> indexedListener,
      @Assisted ProjectIndexCollection indexes) {
    this(projectCache, indexedListener, indexes, true);
  }

  @AssistedInject
  ProjectIndexerImpl(
      ProjectCache projectCache,
      PluginSetContext<ProjectIndexedListener> indexedListener,
      @Assisted ProjectIndexCollection indexes,
      @Assisted boolean notifyListeners) {
    this(projectCache, indexedListener, indexes, null, notifyListeners);
  }

  @AssistedInject
  ProjectIndexerImpl(
      ProjectCache projectCache,
      PluginSetContext<ProjectIndexedListener> indexedListener,
      @Assisted @Nullable ProjectIndex index) {
    this(projectCache, indexedListener, null, index, true);
  }

  private ProjectIndexerImpl(
      ProjectCache projectCache,
      PluginSetContext<ProjectIndexedListener> indexedListener,
      ProjectIndexCollection indexes,
      ProjectIndex index,
      boolean notifyListeners) {
    this.projectCache = projectCache;
    this.indexedListener = indexedListener;
    this.indexes = indexes;
    this.index = index;
    this.notifyListeners = notifyListeners;
  }

  @Override
  public void index(Project.NameKey nameKey) {
    Optional<ProjectState> projectState = projectCache.get(nameKey);
    if (projectState.isPresent()) {
      logger.atFine().log("Replace project %s in index", nameKey.get());
      ProjectData projectData = projectState.get().toProjectData();
      for (ProjectIndex i : getWriteIndexes()) {
        try (TraceTimer traceTimer =
            TraceContext.newTimer(
                "Replacing project",
                Metadata.builder()
                    .projectName(nameKey.get())
                    .indexVersion(i.getSchema().getVersion())
                    .build())) {
          i.replace(projectData);
        } catch (RuntimeException e) {
          throw new StorageException(
              String.format(
                  "Failed to replace project %s in index version %d",
                  nameKey.get(), i.getSchema().getVersion()),
              e);
        }
      }
      fireProjectIndexedEvent(nameKey.get());
    } else {
      logger.atFine().log("Delete project %s from index", nameKey.get());
      for (ProjectIndex i : getWriteIndexes()) {
        try (TraceTimer traceTimer =
            TraceContext.newTimer(
                "Deleting project",
                Metadata.builder()
                    .projectName(nameKey.get())
                    .indexVersion(i.getSchema().getVersion())
                    .build())) {
          i.delete(nameKey);
        } catch (RuntimeException e) {
          throw new StorageException(
              String.format(
                  "Failed to delete project %s from index version %d",
                  nameKey.get(), i.getSchema().getVersion()),
              e);
        }
      }
    }
  }

  private void fireProjectIndexedEvent(String name) {
    if (notifyListeners) {
      indexedListener.runEach(l -> l.onProjectIndexed(name));
    }
  }

  private Collection<ProjectIndex> getWriteIndexes() {
    if (indexes != null) {
      return indexes.getWriteIndexes();
    }

    return index != null ? Collections.singleton(index) : ImmutableSet.of();
  }
}
