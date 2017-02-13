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

import static org.eclipse.jgit.lib.RefDatabase.ALL;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@Singleton
public class Index implements RestModifyView<ProjectResource, ProjectInput> {

  private final ChangeData.Factory changeDataFactory;
  private final ChangeIndexer indexer;
  private final ChangeNotes.Factory notesFactory;
  private final GitRepositoryManager repoManager;
  private final Provider<InternalChangeQuery> queryProvider;
  private final SchemaFactory<ReviewDb> schemaFactory;

  @Inject
  Index(
      ChangeData.Factory changeDataFactory,
      ChangeIndexer indexer,
      ChangeNotes.Factory notesFactory,
      GitRepositoryManager repoManager,
      Provider<InternalChangeQuery> queryProvider,
      SchemaFactory<ReviewDb> schemaFactory) {
    this.changeDataFactory = changeDataFactory;
    this.indexer = indexer;
    this.notesFactory = notesFactory;
    this.repoManager = repoManager;
    this.queryProvider = queryProvider;
    this.schemaFactory = schemaFactory;
  }

  @Override
  public Response<?> apply(ProjectResource resource, ProjectInput input)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    index(resource.getNameKey());
    return Response.none();
  }

  private void index(Project.NameKey project) throws Exception {
    ListMultimap<ObjectId, ChangeData> byId = MultimapBuilder.hashKeys().arrayListValues().build();
    try (Repository repo = repoManager.openRepository(project);
        ReviewDb db = schemaFactory.open()) {
      Map<String, Ref> refs = repo.getRefDatabase().getRefs(ALL);
      for (ChangeNotes cn : notesFactory.scan(repo, db, project)) {
        Ref r = refs.get(cn.getChange().currentPatchSetId().toRefName());
        if (r != null) {
          byId.put(r.getObjectId(), changeDataFactory.create(db, cn));
        }
      }
      Set<Change.Id> alreadyPresentOnIndex = fromIndex(project);
      Set<Change.Id> toReIndex =
          byId.values().stream().map(cd -> cd.getId()).collect(Collectors.toSet());
      Set<Change.Id> toDeleteFormIndex = Sets.difference(alreadyPresentOnIndex, toReIndex);
      index(repo, byId);
      deleteFromIndex(toDeleteFormIndex);
    } catch (Exception e) {
      throw new Exception("Unable to index " + project.get(), e);
    }
  }

  private void index(Repository repo, ListMultimap<ObjectId, ChangeData> byId) throws Exception {
    try (RevWalk walk = new RevWalk(repo)) {
      for (Ref ref : repo.getRefDatabase().getRefs(Constants.R_HEADS).values()) {
        RevObject o = walk.parseAny(ref.getObjectId());
        if (o instanceof RevCommit) {
          walk.markStart((RevCommit) o);
        }
      }

      RevCommit bCommit;
      while ((bCommit = walk.next()) != null && !byId.isEmpty()) {
        if (byId.containsKey(bCommit)) {
          indexCds(byId.get(bCommit));
          byId.removeAll(bCommit);
        }
      }

      for (ObjectId id : byId.keySet()) {
        indexCds(byId.get(id));
      }
    }
  }

  private void indexCds(List<ChangeData> cds) throws IOException {
    for (ChangeData cd : cds) {
      indexer.index(cd);
    }
  }

  private Set<Change.Id> fromIndex(Project.NameKey project) throws OrmException {
    List<ChangeData> cds =
        queryProvider
            .get()
            .setRequestedFields(ImmutableSet.of(ChangeField.CHANGE.getName()))
            .byProject(project);
    return cds.stream().map(cd -> cd.getId()).collect(Collectors.toSet());
  }

  private void deleteFromIndex(Set<Change.Id> toDeleteFormIndex) throws IOException {
    for (Change.Id id : toDeleteFormIndex) {
      indexer.delete(id);
    }
  }
}
