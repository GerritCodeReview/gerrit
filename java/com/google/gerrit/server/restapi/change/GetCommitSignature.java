// Copyright (C) 2026 The Android Open Source Project
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

import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;

import com.google.gerrit.extensions.common.CommitSignatureInfo;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.CommitSignatureVerifier;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.lib.Repository;

public class GetCommitSignature implements RestReadView<RevisionResource> {
  private final GitRepositoryManager repoManager;
  private final CommitSignatureVerifier verifier;
  private final AccountLoader.Factory accountLoaderFactory;

  @Inject
  GetCommitSignature(
      GitRepositoryManager repoManager,
      CommitSignatureVerifier verifier,
      AccountLoader.Factory accountLoaderFactory) {
    this.repoManager = repoManager;
    this.verifier = verifier;
    this.accountLoaderFactory = accountLoaderFactory;
  }

  @Override
  public Response<CommitSignatureInfo> apply(RevisionResource rsrc)
      throws IOException, PermissionBackendException, ResourceNotFoundException {
    if (!verifier.isEnabled()) {
      throw new NotImplementedException("Commit signature verification is disabled");
    }
    if (rsrc.getEdit().isPresent()) {
      throw new ResourceNotFoundException("Only stored patch sets have signature results");
    }
    try (Repository repo = repoManager.openRepository(rsrc.getProject())) {
      CommitSignatureInfo info =
          verifier.verify(repo.open(rsrc.getPatchSet().commitId(), OBJ_COMMIT).getBytes());
      if (info.signer != null) {
        accountLoaderFactory.create(true).fill(List.of(info.signer));
      }
      // Deliberately do not cache: key registration and server policy can change.
      return Response.ok(info);
    }
  }
}
