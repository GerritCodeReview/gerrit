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

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.quota.QuotaBackend;
import com.google.inject.Inject;
import java.util.OptionalLong;

public class RepositorySizeQuotaEnforcer {
  private static final String REPOSITORY_SIZE_GROUP = "/repository:size";

  private final QuotaBackend quotaBackend;

  @Inject
  RepositorySizeQuotaEnforcer(QuotaBackend quotaBackend) {
    this.quotaBackend = quotaBackend;
  }

  public OptionalLong getAvailableRepositorySize(CurrentUser user, Project.NameKey project) {
    return quotaBackend
        .user(user)
        .project(project)
        .availableTokens(REPOSITORY_SIZE_GROUP)
        .availableTokens();
  }

  public void requestSize(CurrentUser user, Project.NameKey project, long size) {
    quotaBackend.user(user).project(project).requestTokens(REPOSITORY_SIZE_GROUP, size);
  }
}
