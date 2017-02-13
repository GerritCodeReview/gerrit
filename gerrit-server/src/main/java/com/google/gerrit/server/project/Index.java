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
import static org.eclipse.jgit.lib.RefDatabase.ALL;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.change.AllChangesIndexer.ProjectIndexer;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@Singleton
public class Index implements RestModifyView<ProjectResource, ProjectInput> {

  private final ChangeData.Factory changeDataFactory;
  private final ChangeIndexer indexer;
  private final ChangeNotes.Factory notesFactory;
  private final GitRepositoryManager repoManager;
  private final ListeningExecutorService executor;
  private final SchemaFactory<ReviewDb> schemaFactory;

  @Inject
  Index(
      ChangeData.Factory changeDataFactory,
      ChangeIndexer indexer,
      ChangeNotes.Factory notesFactory,
      GitRepositoryManager repoManager,
      @IndexExecutor(BATCH) ListeningExecutorService executor,
      SchemaFactory<ReviewDb> schemaFactory) {
    this.changeDataFactory = changeDataFactory;
    this.indexer = indexer;
    this.notesFactory = notesFactory;
    this.repoManager = repoManager;
    this.executor = executor;
    this.schemaFactory = schemaFactory;
  }

  @Override
  public Response<?> apply(ProjectResource resource, ProjectInput input)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    index(resource.getNameKey());
    return Response.none();
  }

  private void index(Project.NameKey project) throws Exception {
    ListMultimap<ObjectId, ChangeData> allById =
        MultimapBuilder.hashKeys().arrayListValues().build();
    try (Repository repo = repoManager.openRepository(project);
        ReviewDb db = schemaFactory.open()) {
      Map<String, Ref> refs = repo.getRefDatabase().getRefs(ALL);
      for (ChangeNotes cn : notesFactory.scan(repo, db, project)) {
        Ref r = refs.get(cn.getChange().currentPatchSetId().toRefName());
        if (r != null) {
          allById.put(r.getObjectId(), changeDataFactory.create(db, cn));
        }
      }
      NullProgressMonitor pm = NullProgressMonitor.INSTANCE;
      PrintWriter pw = new PrintWriter(CharStreams.nullWriter());
      executor.submit(new ProjectIndexer(indexer, allById, repo, pm, pm, pw));
    } catch (RepositoryNotFoundException e) {
      throw new Exception("Repository does not exist" + project.get(), e);
    } catch (IOException | OrmException e) {
      throw new Exception("Unable to index" + project.get(), e);
    }
  }
}
