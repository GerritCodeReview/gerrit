// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import static java.util.stream.Collectors.toMap;

import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.common.CommitMessageInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class GetMessage implements RestReadView<ChangeResource> {
  private final GitRepositoryManager repositoryManager;
  private final PatchSetUtil psUtil;

  @Inject
  GetMessage(GitRepositoryManager repositoryManager, PatchSetUtil psUtil) {
    this.repositoryManager = repositoryManager;
    this.psUtil = psUtil;
  }

  @Override
  public Response<CommitMessageInfo> apply(ChangeResource resource)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    CommitMessageInfo commitMessageInfo = new CommitMessageInfo();
    commitMessageInfo.subject = resource.getChange().getSubject();

    PatchSet ps = psUtil.current(resource.getNotes());

    try (Repository repository = repositoryManager.openRepository(resource.getProject());
        RevWalk revWalk = new RevWalk(repository)) {
      RevCommit patchSetCommit = revWalk.parseCommit(ps.commitId());

      commitMessageInfo.fullMessage = patchSetCommit.getFullMessage();
      commitMessageInfo.footers =
          patchSetCommit.getFooterLines().stream()
              .collect(toMap(FooterLine::getKey, FooterLine::getValue));
    }

    return Response.ok(commitMessageInfo);
  }
}
