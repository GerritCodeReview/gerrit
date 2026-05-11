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

import com.google.common.annotations.VisibleForTesting;
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
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.restapi.config.IndexChanges.Input;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@Singleton
public class IndexChanges implements RestModifyView<ConfigResource, Input> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Input {
    public Set<String> changes;
    @VisibleForTesting public boolean deleteMissing;
  }

  private final ChangeFinder changeFinder;
  private final ChangeData.Factory changeDataFactory;
  private final ChangeIndexer indexer;

  @Inject
  IndexChanges(
      ChangeFinder changeFinder, ChangeData.Factory changeDataFactory, ChangeIndexer indexer) {
    this.changeFinder = changeFinder;
    this.changeDataFactory = changeDataFactory;
    this.indexer = indexer;
  }

  @Override
  public Response<String> apply(ConfigResource resource, Input input) throws BadRequestException {
    if (input == null || input.changes == null) {
      return Response.ok("Nothing to index");
    }

    List<ChangeIdentifier> changeIds = new ArrayList<>();
    for (String id : input.changes) {
      changeIds.add(getChangeIdentifier(id));
    }

    for (ChangeIdentifier changeId : changeIds) {
      List<ChangeNotes> notes = changeFinder.find(String.valueOf(changeId.changeId().get()));

      if (notes.isEmpty()) {
        logger.atWarning().log("Change %s missing in NoteDb", changeId.changeId());
        if (input.deleteMissing) {
          logger.atWarning().log("Deleting change %s from index", changeId.changeId());
          ChangeData cd = changeDataFactory.create(changeId.project(), changeId.changeId());
          indexer.delete(cd.virtualId());
        }
        continue;
      }

      for (ChangeNotes n : notes) {
        indexer.index(changeDataFactory.create(n));
        logger.atFine().log("Indexed change %s", changeId.changeId());
      }
    }

    return Response.ok("Indexed changes " + input.changes);
  }

  record ChangeIdentifier(Project.NameKey project, Change.Id changeId) {}

  ChangeIdentifier getChangeIdentifier(String id) throws BadRequestException {
    int tilde = id.indexOf('~');
    Change.Id changeId =
        Change.Id.tryParse(tilde >= 0 ? id.substring(tilde + 1) : "")
            .orElseThrow(
                () ->
                    new BadRequestException(
                        "Change ID must be in project~changeNumber format: " + id));
    return new ChangeIdentifier(Project.nameKey(id.substring(0, tilde)), changeId);
  }
}
