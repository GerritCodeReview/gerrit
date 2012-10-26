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

package com.google.gerrit.server;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;

import java.util.List;

public interface RecentlyAccessedCache {

  /**
   * Adds a project as recent project for the given user to the cache.
   *
   * @param accountId the account id of the user for which the project should be
   *        added
   * @param project the name of the project that should be added
   */
  public void add(final Account.Id accountId, final Project.NameKey project);

  /**
   * Returns the projects that the given user has recently accessed.
   *
   * @param accountId the account id of the user for which the recent projects
   *        should be returned
   */
  public List<Project.NameKey> getProjects(final Account.Id accountId);
}
