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

package com.google.gerrit.server.project;

import static com.google.gerrit.server.git.QueueProvider.QueueType.BATCH;

import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.MultiProgressMonitor;
import com.google.gerrit.server.git.MultiProgressMonitor.Task;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.change.AllChangesIndexer;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.PrintWriter;
import java.util.concurrent.Future;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@Singleton
public class Index implements RestModifyView<ProjectResource, ProjectInput> {

  private final AllChangesIndexer allChangesIndexer;
  private final ChangeIndexer indexer;
  private final ListeningExecutorService executor;

  @Inject
  Index(
      AllChangesIndexer allChangesIndexer,
      ChangeIndexer indexer,
      @IndexExecutor(BATCH) ListeningExecutorService executor) {
    this.allChangesIndexer = allChangesIndexer;
    this.indexer = indexer;
    this.executor = executor;
  }

  @Override
  public Response.Accepted apply(ProjectResource resource, ProjectInput input) {
    Project.NameKey project = resource.getNameKey();
    Task mpt =
        new MultiProgressMonitor(ByteStreams.nullOutputStream(), "Reindexing project")
            .beginSubTask("", MultiProgressMonitor.UNKNOWN);
    PrintWriter pw = new PrintWriter(CharStreams.nullWriter());
    // The REST call is just a trigger for async reindexing, so it is safe to ignore the future's
    // return value.
    @SuppressWarnings("unused")
    Future<Void> ignored =
        executor.submit(allChangesIndexer.reindexProject(indexer, project, mpt, mpt, pw));
    return Response.accepted("Project " + project + " submitted for reindexing");
  }
}
