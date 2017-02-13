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

package com.google.gerrit.sshd.commands;

import static com.google.gerrit.common.data.GlobalCapability.MAINTAIN_SERVER;
import static org.eclipse.jgit.lib.RefDatabase.ALL;

import java.io.IOException;
import java.util.ArrayList;
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
import org.kohsuke.args4j.Argument;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.annotations.RequiresAnyCapability;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;

@RequiresAnyCapability({MAINTAIN_SERVER})
@CommandMetaData(name = "project", description = "Index changes of a project")
final class IndexProjectCommand extends SshCommand {

  @Inject
  private ChangeIndexer indexer;

  @Inject
  private GitRepositoryManager repoManager;

  @Inject
  private SchemaFactory<ReviewDb> schemaFactory;

  @Inject
  private ChangeNotes.Factory notesFactory;

  @Inject
  private ChangeData.Factory changeDataFactory;

  @Inject
  private Provider<InternalChangeQuery> queryProvider;

  @Argument(
      index = 0,
      required = true,
      multiValued = true,
      metaVar = "PROJECT",
      usage = "projects for which the changes should be indexed"
  )
  private List<ProjectControl> projects = new ArrayList<>();

  @Override
  protected void run() throws UnloggedFailure, Failure, Exception {
    if (projects.isEmpty()) {
      throw die("needs at least one project as command arguments");
    }
    projects.stream().map(pc -> pc.getProject().getNameKey()).forEach(p -> index(p));
  }

  private void index(Project.NameKey project) {
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
      Set<Change.Id> toReIndex = byId.values().stream().map(cd -> cd.getId()).collect(Collectors.toSet());
      Set<Change.Id> toDeleteFormIndex = Sets.difference(alreadyPresentOnIndex, toReIndex);
      index(repo, byId);
      deleteFromIndex(toDeleteFormIndex);
    } catch (Exception e) {
      writeError("error", String.format("Unable to index %s: %s", project.get(), e.getMessage()));
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
    List<ChangeData> cds = queryProvider.get()
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
