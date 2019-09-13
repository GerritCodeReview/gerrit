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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.restapi.config.IndexChanges.Input;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Set;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@Singleton
public class IndexChanges implements RestModifyView<ConfigResource, Input> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Input {
    public Set<String> changes;
  }

  private final ChangeFinder changeFinder;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final ChangeData.Factory changeDataFactory;
  private final ChangeIndexer indexer;

  @Inject
  IndexChanges(
      ChangeFinder changeFinder,
      SchemaFactory<ReviewDb> schemaFactory,
      ChangeData.Factory changeDataFactory,
      ChangeIndexer indexer) {
    this.changeFinder = changeFinder;
    this.schemaFactory = schemaFactory;
    this.changeDataFactory = changeDataFactory;
    this.indexer = indexer;
  }

  @Override
  public Object apply(ConfigResource resource, Input input) throws OrmException {
    if (input == null || input.changes == null) {
      return Response.ok("Nothing to index");
    }

    try (ReviewDb db = schemaFactory.open()) {
      for (String id : input.changes) {
        for (ChangeNotes n : changeFinder.find(id)) {
          try {
            indexer.index(changeDataFactory.create(db, n));
            logger.atFine().log("Indexed change %s", id);
          } catch (IOException e) {
            logger.atSevere().withCause(e).log("Failed to index change %s", id);
          }
        }
      }
    }

    return Response.ok("Indexed changes " + input.changes);
  }
}
