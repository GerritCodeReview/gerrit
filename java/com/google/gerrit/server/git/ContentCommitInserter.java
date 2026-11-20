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

package com.google.gerrit.server.git;

import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;

/** Shared insertion boundary for finalized repository-content commits. Does not flush objects. */
@Singleton
public class ContentCommitInserter {
  private final SignedCommitPolicy signedCommitPolicy;
  private final ServerCommitSigner signer;

  @Inject
  ContentCommitInserter(SignedCommitPolicy signedCommitPolicy, ServerCommitSigner signer) {
    this.signedCommitPolicy = signedCommitPolicy;
    this.signer = signer;
  }

  public ObjectId insert(BranchNameKey destination, ObjectInserter inserter, CommitBuilder commit)
      throws IOException, ResourceConflictException {
    return insert(destination, inserter, commit.build());
  }

  public ObjectId insert(BranchNameKey destination, ObjectInserter inserter, byte[] rawCommit)
      throws IOException, ResourceConflictException {
    if (signedCommitPolicy.isRequired(destination)) {
      rawCommit = signer.signIfUnsigned(rawCommit);
    }
    signedCommitPolicy.checkGeneratedCommit(destination, rawCommit);
    return inserter.insert(OBJ_COMMIT, rawCommit);
  }

  /** Finalizes and checks initial content before creating a repository or its metadata refs. */
  public byte[] prepareInitialCommit(
      Project project, Collection<String> branches, CommitBuilder commit)
      throws IOException, ResourceConflictException {
    byte[] rawCommit = commit.build();
    for (String branch : branches) {
      if (signedCommitPolicy.isInitialRequired(project, branch)) {
        rawCommit = signer.signIfUnsigned(rawCommit);
        break;
      }
    }
    for (String branch : branches) {
      signedCommitPolicy.checkInitialCommit(project, branch, rawCommit);
    }
    return rawCommit;
  }
}
