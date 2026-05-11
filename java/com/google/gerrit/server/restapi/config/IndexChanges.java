// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.restapi.config;

import com.google.common.base.Preconditions;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.restapi.config.IndexChanges.Input;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@Singleton
public class IndexChanges implements RestModifyView<ConfigResource, Input> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Pattern PROJECT_WITH_CHANGE_NUM_REGEX = Pattern.compile("^([^~]+)~(\\d+)$");

  public record Input(Set<String> changes, boolean deleteMissing) {

    public Input() {
      this(Collections.emptySet(), false);
    }
  }

  private final ChangeFinder changeFinder;
  private final ChangeData.Factory changeDataFactory;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeNotes.Factory notesFactory;
  private final ChangeIndexer indexer;

  @Inject
  IndexChanges(
      ChangeFinder changeFinder,
      ChangeData.Factory changeDataFactory,
      Provider<InternalChangeQuery> queryProvider,
      ChangeNotes.Factory notesFactory,
      ChangeIndexer indexer) {
    this.changeFinder = changeFinder;
    this.changeDataFactory = changeDataFactory;
    this.queryProvider = queryProvider;
    this.notesFactory = notesFactory;
    this.indexer = indexer;
  }

  @Override
  public Response<String> apply(ConfigResource resource, Input input) throws Exception {
    if (input == null || input.changes == null) {
      return Response.ok("Nothing to index");
    }

    if (input.deleteMissing) {
      List<ProjectWithChangeNumTuple> changeIds = new ArrayList<>();
      for (String id : input.changes) {
        changeIds.add(parseIntoProjectWithChangeNumTuple(id));
      }

      for (ProjectWithChangeNumTuple changeInfo : changeIds) {
        List<ChangeData> changes =
            queryProvider.get().byProjectChangeNumber(changeInfo.project(), changeInfo.changeId());
        Preconditions.checkState(
            changes.size() <= 1,
            "Ambiguous change ID %s in project %s",
            changeInfo.changeId(),
            changeInfo.project());

        if (!changes.isEmpty()) {
          try {
            // Probe NoteDb: NoSuchChangeException means the change is in the index
            // but absent from disk, so it should be deleted.
            ChangeNotes unused = notesFactory.create(changeInfo.project(), changeInfo.changeId());
          } catch (NoSuchChangeException e) {
            logger.atWarning().log(
                "Change %s~%s missing in NoteDb", changeInfo.project(), changeInfo.changeId());
            ChangeData cd = changes.getFirst();
            logger.atWarning().log(
                "Deleting change %s~%s from index", cd.project(), cd.change().getChangeId());
            indexer.delete(cd.virtualId());
            continue;
          }
        }

        indexer.index(changeInfo.project, changeInfo.changeId);
        logger.atFine().log("Indexed change %s:%s", changeInfo.project, changeInfo.changeId);
      }
    } else {
      input.changes.stream()
          .flatMap(cid -> changeFinder.find(cid).stream())
          .map(changeDataFactory::create)
          .forEach(
              cd -> {
                indexer.index(cd);
                logger.atFine().log("Indexed change %s:%s", cd.project(), cd.getId());
              });
    }

    return Response.ok("Indexed changes " + input.changes);
  }

  record ProjectWithChangeNumTuple(Project.NameKey project, Change.Id changeId) {}

  ProjectWithChangeNumTuple parseIntoProjectWithChangeNumTuple(String id)
      throws BadRequestException {
    Matcher projectWithChangeNumMatcher = PROJECT_WITH_CHANGE_NUM_REGEX.matcher(id);
    if (projectWithChangeNumMatcher.matches()) {
      return new ProjectWithChangeNumTuple(
          Project.nameKey(projectWithChangeNumMatcher.group(1)),
          Change.id(Integer.parseInt(projectWithChangeNumMatcher.group(2))));
    }
    throw new BadRequestException("Change ID must be in project~changeNumber format: " + id);
  }
}
