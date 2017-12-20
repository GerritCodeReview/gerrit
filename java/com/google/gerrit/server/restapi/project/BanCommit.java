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

package com.google.gerrit.server.restapi.project;

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.api.projects.BanCommitInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.git.BanCommitResult;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.project.BanCommit.BanResultInfo;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;

@Singleton
public class BanCommit
    extends RetryingRestModifyView<ProjectResource, BanCommitInput, BanResultInfo> {
  private final com.google.gerrit.server.git.BanCommit banCommit;

  @Inject
  BanCommit(RetryHelper retryHelper, com.google.gerrit.server.git.BanCommit banCommit) {
    super(retryHelper);
    this.banCommit = banCommit;
  }

  @Override
  protected BanResultInfo applyImpl(
      BatchUpdate.Factory updateFactory, ProjectResource rsrc, BanCommitInput input)
      throws RestApiException, UpdateException, IOException, PermissionBackendException {
    BanResultInfo r = new BanResultInfo();
    if (input != null && input.commits != null && !input.commits.isEmpty()) {
      List<ObjectId> commitsToBan = new ArrayList<>(input.commits.size());
      for (String c : input.commits) {
        try {
          commitsToBan.add(ObjectId.fromString(c));
        } catch (IllegalArgumentException e) {
          throw new UnprocessableEntityException(e.getMessage());
        }
      }

      BanCommitResult result =
          banCommit.ban(rsrc.getNameKey(), rsrc.getUser(), commitsToBan, input.reason);
      r.newlyBanned = transformCommits(result.getNewlyBannedCommits());
      r.alreadyBanned = transformCommits(result.getAlreadyBannedCommits());
      r.ignored = transformCommits(result.getIgnoredObjectIds());
    }
    return r;
  }

  private static List<String> transformCommits(List<ObjectId> commits) {
    if (commits == null || commits.isEmpty()) {
      return null;
    }
    return Lists.transform(commits, ObjectId::getName);
  }

  public static class BanResultInfo {
    public List<String> newlyBanned;
    public List<String> alreadyBanned;
    public List<String> ignored;
  }
}
