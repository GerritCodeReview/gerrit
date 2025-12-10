// Copyright (C) 2025 The Android Open Source Project
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

import static com.google.gerrit.common.data.GlobalCapability.MAINTAIN_SERVER;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.index.project.ProjectIndex;
import com.google.gerrit.index.project.ProjectIndexCollection;
import com.google.gerrit.server.config.IndexResource;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@RequiresCapability(MAINTAIN_SERVER)
@Singleton
public class CommitIndex implements RestModifyView<IndexResource, Input> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AccountIndexCollection accountIndexCollection;
  private final ChangeIndexCollection changeIndexCollection;
  private final GroupIndexCollection groupIndexCollection;
  private final ProjectIndexCollection projectIndexCollection;

  @Inject
  public CommitIndex(
      ChangeIndexCollection changeIndexCollection,
      AccountIndexCollection accountIndexCollection,
      GroupIndexCollection groupIndexCollection,
      ProjectIndexCollection projectIndexCollection) {
    this.changeIndexCollection = changeIndexCollection;
    this.accountIndexCollection = accountIndexCollection;
    this.groupIndexCollection = groupIndexCollection;
    this.projectIndexCollection = projectIndexCollection;
  }

  @Override
  public Response<?> apply(IndexResource resource, Input input)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {

    String indexName = resource.getIndexDefinition().getName();
    String responsePayload = "";
    switch (indexName) {
      case "changes" -> {
        for (ChangeIndex changeIndex : changeIndexCollection.getWriteIndexes()) {
          changeIndex.flushAndCommit();
        }
      }
      case "accounts" -> {
        for (AccountIndex accountIndex : accountIndexCollection.getWriteIndexes()) {
          accountIndex.flushAndCommit();
        }
      }
      case "groups" -> {
        for (GroupIndex groupIndex : groupIndexCollection.getWriteIndexes()) {
          groupIndex.flushAndCommit();
        }
      }
      case "projects" -> {
        for (ProjectIndex projectIndex : projectIndexCollection.getWriteIndexes()) {
          projectIndex.flushAndCommit();
        }
      }
      default -> {
        responsePayload = String.format("Invalid index name: %s", indexName);
        logger.atWarning().log("Invalid index name: %s", indexName);
      }
    }

    return Response.ok(responsePayload);
  }
}
