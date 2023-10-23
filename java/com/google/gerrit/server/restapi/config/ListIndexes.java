// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.index.project.ProjectIndexCollection;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.gerrit.server.restapi.config.IndexCollection.IndexType;
import com.google.inject.Inject;
import java.util.Map;
import java.util.TreeMap;

@RequiresCapability(MAINTAIN_SERVER)
public class ListIndexes implements RestReadView<ConfigResource> {
  private final AccountIndexCollection accountIndexes;
  private final ChangeIndexCollection changeIndexes;
  private final GroupIndexCollection groupIndexes;
  private final ProjectIndexCollection projectIndexes;

  @Inject
  public ListIndexes(
      AccountIndexCollection accountIndexes,
      ChangeIndexCollection changeIndexes,
      GroupIndexCollection groupIndexes,
      ProjectIndexCollection projectIndexes) {
    this.accountIndexes = accountIndexes;
    this.changeIndexes = changeIndexes;
    this.groupIndexes = groupIndexes;
    this.projectIndexes = projectIndexes;
  }

  private Map<IndexType, IndexInfo> getIndexInfos() {
    Map<IndexType, IndexInfo> indexInfos = new TreeMap<>();
    indexInfos.put(
        IndexType.ACCOUNTS, IndexInfo.fromIndexCollection(IndexType.ACCOUNTS, accountIndexes));
    indexInfos.put(
        IndexType.CHANGES, IndexInfo.fromIndexCollection(IndexType.CHANGES, changeIndexes));
    indexInfos.put(IndexType.GROUPS, IndexInfo.fromIndexCollection(IndexType.GROUPS, groupIndexes));
    indexInfos.put(
        IndexType.PROJECTS, IndexInfo.fromIndexCollection(IndexType.PROJECTS, projectIndexes));
    return indexInfos;
  }

  @Override
  public Response<Object> apply(ConfigResource rsrc) {
    return Response.ok(getIndexInfos());
  }
}
