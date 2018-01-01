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
package com.google.gerrit.server.change;

import static com.google.gerrit.server.git.QueueProvider.QueueType.BATCH;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeQueryInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexBy implements RestModifyView<TopLevelResource, ChangeQueryInput> {
  private static final Logger log = LoggerFactory.getLogger(IndexBy.class);

  private final Provider<ReviewDb> db;
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> user;
  private final Provider<QueryChanges> queryFactory;
  private final ChangeIndexer indexer;
  private final ListeningExecutorService executor;
  private final OneOffRequestContext requestContext;
  private final ChangeNotes.Factory changeNotesFactory;

  @Inject
  IndexBy(
      Provider<ReviewDb> db,
      PermissionBackend permissionBackend,
      Provider<CurrentUser> user,
      Provider<QueryChanges> queryFactory,
      ChangeIndexer indexer,
      OneOffRequestContext requestContext,
      ChangeNotes.Factory changeNotesFactory,
      @IndexExecutor(BATCH) ListeningExecutorService executor) {
    this.db = db;
    this.permissionBackend = permissionBackend;
    this.user = user;
    this.queryFactory = queryFactory;
    this.indexer = indexer;
    this.executor = executor;
    this.requestContext = requestContext;
    this.changeNotesFactory = changeNotesFactory;
  }

  @Override
  public Response.Accepted apply(TopLevelResource resource, ChangeQueryInput input)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    permissionBackend.user(user).check(GlobalPermission.MAINTAIN_SERVER);
    QueryChanges queryChanges = queryFactory.get();
    queryChanges.addQuery(input.query);
    @SuppressWarnings("unchecked")
    List<ChangeInfo> changes = (List<ChangeInfo>) queryChanges.apply(resource);
    executor.submit(
        () -> {
          for (ChangeInfo changeInfo : changes) {
            Change.Id changeId = new Change.Id(changeInfo._number);
            try (ManualRequestContext ctx = requestContext.open()) {
              ChangeNotes changeNotes = getChangeNotes(changeInfo, changeId);
              if (changeNotes == null) {
                indexer.delete(changeId);
                log.warn(
                    "Change {} was not found in the database or NoteDb and was removed from the index",
                    changeId);
              } else {
                indexer.index(db.get(), changeNotes.getChange());
                log.debug("Change {} indexed", changeId);
              }
            } catch (OrmException | IOException e) {
              log.error("Unable to index change {}", changeInfo.id, e);
            }
          }
        });
    return Response.accepted("Changes submitted for indexing");
  }

  private ChangeNotes getChangeNotes(ChangeInfo changeInfo, Change.Id changeId) {
    try {
      return changeNotesFactory.createChecked(
          db.get(), new Project.NameKey(changeInfo.project), changeId);
    } catch (OrmException e) {
      log.warn("Unable to read change {} from the database or NoteDb", changeId, e);
      return null;
    }
  }
}
