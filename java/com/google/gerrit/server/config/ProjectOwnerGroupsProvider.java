// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.config;

import com.google.gerrit.config.RepositoryConfig;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.util.ServerRequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/**
 * Provider of the group(s) which should become owners of a newly created project. The only matching
 * patterns supported are exact match or wildcard matching which can be specified by ending the name
 * with a {@code *}.
 *
 * <pre>
 * [repository &quot;*&quot;]
 *     ownerGroup = Registered Users
 *     ownerGroup = Administrators
 * [repository &quot;project/*&quot;]
 *     ownerGroup = Administrators
 * </pre>
 */
public class ProjectOwnerGroupsProvider extends GroupSetProvider {

  public interface Factory {
    ProjectOwnerGroupsProvider create(Project.NameKey project);
  }

  @Inject
  public ProjectOwnerGroupsProvider(
      GroupBackend gb,
      ThreadLocalRequestContext context,
      ServerRequestContext serverCtx,
      RepositoryConfig repositoryCfg,
      @Assisted Project.NameKey project) {
    super(gb, context, serverCtx, repositoryCfg.getOwnerGroups(project));
  }
}
