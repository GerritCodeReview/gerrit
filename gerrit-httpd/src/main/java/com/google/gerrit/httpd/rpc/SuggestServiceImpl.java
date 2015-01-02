// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.SuggestService;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;
import java.util.List;

class SuggestServiceImpl extends BaseServiceImplementation implements
    SuggestService {
  private final ProjectControl.Factory projectControlFactory;
  private final GroupBackend groupBackend;

  @Inject
  SuggestServiceImpl(final Provider<ReviewDb> schema,
      final Provider<CurrentUser> currentUser,
      final ProjectControl.Factory projectControlFactory,
      final GroupBackend groupBackend) {
    super(schema, currentUser);
    this.projectControlFactory = projectControlFactory;
    this.groupBackend = groupBackend;
  }

  @Override
  public void suggestAccountGroupForProject(final Project.NameKey project,
      final String query, final int limit,
      final AsyncCallback<List<GroupReference>> callback) {
    run(callback, new Action<List<GroupReference>>() {
      @Override
      public List<GroupReference> run(final ReviewDb db) {
        ProjectControl projectControl = null;
        if (project != null) {
          try {
            projectControl = projectControlFactory.controlFor(project);
          } catch (NoSuchProjectException e) {
            return Collections.emptyList();
          }
        }
        return suggestAccountGroup(projectControl, query, limit);
      }
    });
  }

  private List<GroupReference> suggestAccountGroup(
      @Nullable final ProjectControl projectControl, final String query, final int limit) {
    return Lists.newArrayList(Iterables.limit(
        groupBackend.suggest(query, projectControl),
        limit <= 0 ? 10 : Math.min(limit, 10)));
  }
}
