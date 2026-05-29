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

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.ChangeIdentifier;
import java.util.function.Function;
import org.eclipse.jgit.lib.ObjectId;

/** Builder to simplify parent specification of a change. */
public class ParentBuilder<T> {
  private final Function<TestCommitIdentifier, T> parentToBuilderAdder;

  public ParentBuilder(Function<TestCommitIdentifier, T> parentToBuilderAdder) {
    this.parentToBuilderAdder = parentToBuilderAdder;
  }

  /** Use the commit identified by the specified SHA-1. */
  public T commit(ObjectId commitSha1) {
    return parentToBuilderAdder.apply(TestCommitIdentifier.ofCommitSha1(commitSha1));
  }

  /**
   * Use the commit which is at the tip of the specified branch. Short branch names (without
   * refs/heads) are automatically expanded.
   */
  public T tipOfBranch(String branchName) {
    return parentToBuilderAdder.apply(TestCommitIdentifier.ofBranch(branchName));
  }

  /**
   * Use the current patchset commit of the indicated change.
   *
   * @deprecated Use {@link #change(ChangeIdentifier)} instead
   */
  // TODO Drop this method when all callers have been migrated to change(ChangeIdentifier)
  @Deprecated
  public T change(Change.Id changeId) {
    return parentToBuilderAdder.apply(TestCommitIdentifier.ofChangeId(changeId));
  }

  /** Use the current patchset commit of the indicated change. */
  public T change(ChangeIdentifier changeIdentifier) {
    return parentToBuilderAdder.apply(TestCommitIdentifier.ofChangeIdentifier(changeIdentifier));
  }

  /** Use the commit identified by the specified patchset. */
  public T patchset(PatchSet.Id patchsetId) {
    return parentToBuilderAdder.apply(TestCommitIdentifier.ofPatchsetId(patchsetId));
  }
}
