// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/** Cache of the projects that users have recently accessed */
public class RecentProjectsCache {
  private static final int MAX = 10;

  private final Map<Account.Id, LinkedList<Project.NameKey>> cache =
      new HashMap<Account.Id, LinkedList<Project.NameKey>>();

  /**
   * Adds a project as recent project for the given user to the cache.
   *
   * @param accountId the account id of the user for which the project should be
   *        added
   * @param project the name of the project that should be added
   */
  public void add(final Account.Id accountId, final Project.NameKey project) {
    LinkedList<Project.NameKey> list = cache.get(accountId);
    if (list == null) {
      list = new LinkedList<Project.NameKey>();
      cache.put(accountId, list);
    }
    while (list.size() >= MAX) {
      list.removeLast();
    }
    list.addFirst(project);
  }

  /**
   * Returns the projects that the given user has recently accessed.
   *
   * @param accountId the account id of the user for which the recent projects
   *        should be returned
   */
  public List<Project.NameKey> get(final Account.Id accountId) {
    List<Project.NameKey> list = cache.get(accountId);
    if (list != null) {
      return list;
    }
    return Collections.emptyList();
  }
}
