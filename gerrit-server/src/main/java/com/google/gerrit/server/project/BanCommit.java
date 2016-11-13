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

package com.google.gerrit.server.project;

import com.google.common.collect.Lists;
import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.git.BanCommitResult;
import com.google.gerrit.server.project.BanCommit.Input;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.lib.ObjectId;

@Singleton
public class BanCommit implements RestModifyView<ProjectResource, Input> {
  public static class Input {
    public List<String> commits;
    public String reason;

    public static Input fromCommits(String firstCommit, String... moreCommits) {
      return fromCommits(Lists.asList(firstCommit, moreCommits));
    }

    public static Input fromCommits(List<String> commits) {
      Input in = new Input();
      in.commits = commits;
      return in;
    }
  }

  private final com.google.gerrit.server.git.BanCommit banCommit;

  @Inject
  BanCommit(com.google.gerrit.server.git.BanCommit banCommit) {
    this.banCommit = banCommit;
  }

  @Override
  public BanResultInfo apply(ProjectResource rsrc, Input input)
      throws UnprocessableEntityException, AuthException, ResourceConflictException, IOException {
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

      try {
        BanCommitResult result = banCommit.ban(rsrc.getControl(), commitsToBan, input.reason);
        r.newlyBanned = transformCommits(result.getNewlyBannedCommits());
        r.alreadyBanned = transformCommits(result.getAlreadyBannedCommits());
        r.ignored = transformCommits(result.getIgnoredObjectIds());
      } catch (PermissionDeniedException e) {
        throw new AuthException(e.getMessage());
      } catch (ConcurrentRefUpdateException e) {
        throw new ResourceConflictException(e.getMessage(), e);
      }
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
