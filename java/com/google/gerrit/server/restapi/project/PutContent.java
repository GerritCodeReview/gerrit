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

package com.google.gerrit.server.restapi.project;

import com.google.gerrit.extensions.api.projects.CommitFilesInput;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.BranchResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

/**
 * Commits a set of file operations directly to a branch in one call. The write counterpart of
 * {@link GetContent}; requires push access.
 */
@Singleton
public class PutContent implements RestModifyView<BranchResource, CommitFilesInput> {
  private final BranchCommitBuilder branchCommitBuilder;

  @Inject
  PutContent(BranchCommitBuilder branchCommitBuilder) {
    this.branchCommitBuilder = branchCommitBuilder;
  }

  @Override
  public Response<CommitInfo> apply(BranchResource rsrc, CommitFilesInput input)
      throws RestApiException, PermissionBackendException, IOException {
    return Response.ok(branchCommitBuilder.commitFiles(rsrc, input));
  }
}
