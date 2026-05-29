// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.change;

import com.google.auto.value.AutoOneOf;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.ChangeIdentifier;
import org.eclipse.jgit.lib.ObjectId;

/** Attributes, each one uniquely identifying a commit. */
@AutoOneOf(TestCommitIdentifier.Kind.class)
public abstract class TestCommitIdentifier {
  public enum Kind {
    COMMIT_SHA_1,
    BRANCH,
    CHANGE_ID,
    CHANGE_IDENTIFIER,
    PATCHSET_ID
  }

  public abstract Kind getKind();

  /** SHA-1 of the commit. */
  public abstract ObjectId commitSha1();

  /** Branch whose tip points to the desired commit. */
  public abstract String branch();

  /** Numeric ID of the change whose current patchset points to the desired commit. */
  public abstract Change.Id changeId();

  /** Identifier of the change whose current patchset points to the desired commit. */
  public abstract ChangeIdentifier changeIdentifier();

  /** ID of the patchset representing the desired commit. */
  public abstract PatchSet.Id patchsetId();

  public static TestCommitIdentifier ofCommitSha1(ObjectId commitSha1) {
    return AutoOneOf_TestCommitIdentifier.commitSha1(commitSha1);
  }

  public static TestCommitIdentifier ofBranch(String branchName) {
    return AutoOneOf_TestCommitIdentifier.branch(branchName);
  }

  public static TestCommitIdentifier ofChangeId(Change.Id changeId) {
    return AutoOneOf_TestCommitIdentifier.changeId(changeId);
  }

  public static TestCommitIdentifier ofChangeIdentifier(ChangeIdentifier changeIdentifier) {
    return AutoOneOf_TestCommitIdentifier.changeIdentifier(changeIdentifier);
  }

  public static TestCommitIdentifier ofPatchsetId(PatchSet.Id patchsetId) {
    return AutoOneOf_TestCommitIdentifier.patchsetId(patchsetId);
  }
}
