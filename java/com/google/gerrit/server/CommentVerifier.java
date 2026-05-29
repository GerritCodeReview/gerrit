// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Comment;
import org.eclipse.jgit.lib.PersonIdent;

/** Verifier for {@link Comment} objects */
public final class CommentVerifier {
  public static void verify(
      Comment c, Account.Id accountId, Account.Id realAccountId, PersonIdent authorIdent) {
    checkArgument(c.getCommitId() != null, "commit ID required for comment: %s", c);
    checkAccountId(accountId, authorIdent);
    checkArgument(
        c.author.getId().equals(accountId),
        "The author for the following comment does not match the author of this CommentVerifier"
            + " (%s): %s",
        accountId,
        c);
    checkArgument(
        c.getRealAuthor().getId().equals(realAccountId),
        "The real author for the following comment does not match the real"
            + " author of this CommentVerifier (%s): %s",
        realAccountId,
        c);
  }

  @CanIgnoreReturnValue
  private static Account.Id checkAccountId(Account.Id accountId, PersonIdent authorIdent) {
    checkState(
        accountId != null,
        "author identity for CommentVerifier is not from an IdentifiedUser: %s",
        authorIdent.toExternalString());
    return accountId;
  }
}
