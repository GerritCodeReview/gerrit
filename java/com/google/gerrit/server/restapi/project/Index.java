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

package com.google.gerrit.server.restapi.project;

import static com.google.gerrit.server.git.QueueProvider.QueueType.BATCH;

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.projects.IndexProjectInput;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.index.project.ProjectIndexer;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.Future;

@RequiresCapability(GlobalCapability.MAINTAIN_SERVER)
@Singleton
public class Index implements RestModifyView<ProjectResource, IndexProjectInput> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ProjectIndexer indexer;
  private final ListeningExecutorService executor;
  private final Provider<ListChildProjects> listChildProjectsProvider;

  @Inject
  Index(
      ProjectIndexer indexer,
      @IndexExecutor(BATCH) ListeningExecutorService executor,
      Provider<ListChildProjects> listChildProjectsProvider) {
    this.indexer = indexer;
    this.executor = executor;
    this.listChildProjectsProvider = listChildProjectsProvider;
  }

  @Override
  public Response.Accepted apply(ProjectResource rsrc, IndexProjectInput input)
      throws IOException, PermissionBackendException, RestApiException {
    String response = "Project " + rsrc.getName() + " submitted for reindexing";

    reindex(rsrc.getNameKey(), input.async);
    if (Boolean.TRUE.equals(input.indexChildren)) {
      for (ProjectInfo child : listChildProjectsProvider.get().withRecursive(true).apply(rsrc)) {
        reindex(new Project.NameKey(child.name), input.async);
      }

      response += " (indexing children recursively)";
    }
    return Response.accepted(response);
  }

  private void reindex(Project.NameKey project, Boolean async) throws IOException {
    if (Boolean.TRUE.equals(async)) {
      @SuppressWarnings("unused")
      Future<?> possiblyIgnoredError =
          executor.submit(
              () -> {
                try {
                  indexer.index(project);
                } catch (IOException e) {
                  logger.atWarning().withCause(e).log("reindexing project %s failed", project);
                }
              });
    } else {
      indexer.index(project);
    }
  }
}
