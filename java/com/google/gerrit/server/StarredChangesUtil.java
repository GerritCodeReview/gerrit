// Copyright (C) 2015 The Android Open Source Project
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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public interface StarredChangesUtil {
  @AutoValue
  abstract class StarField {
    private static final String SEPARATOR = ":";

    @Nullable
    public static StarField parse(String s) {
      Integer id;
      int p = s.indexOf(SEPARATOR);
      if (p >= 0) {
        id = Ints.tryParse(s.substring(0, p));
      } else {
        // NOTE: This code branch should not be removed. This code is used internally by Google and
        // must not be changed without approval from a Google contributor. In
        // 992877d06d3492f78a3b189eb5579ddb86b9f0da we accidentally changed index writing to write
        // <account_id> instead of <account_id>:star. As some servers have picked that up and wrote
        // index entries with the short format, we should keep support its parsing.
        id = Ints.tryParse(s);
      }
      if (id == null) {
        return null;
      }
      return create(Account.id(id));
    }

    public static StarField create(Account.Id accountId) {
      return new AutoValue_StarredChangesUtil_StarField(accountId);
    }

    public abstract Account.Id accountId();

    @Override
    public final String toString() {
      // NOTE: The ":star" addition is used internally by Google and must not be removed without
      // approval from a Google contributor. This method is used for writing change index data.
      // Historically, we supported different kinds of labels, which were stored in this
      // format, with "star" being the only label in use. This label addition stayed in order to
      // keep the index format consistent while removing the star-label support.
      return accountId() + SEPARATOR + "star";
    }
  }

  boolean isStarred(Account.Id accountId, Change.Id changeId);

  /**
   * Returns a subset of change IDs among the input {@code changeIds} list that are starred by the
   * {@code caller} user.
   */
  Set<Change.Id> areStarred(Repository allUsersRepo, List<Change.Id> changeIds, Account.Id caller);

  ImmutableMap<Account.Id, Ref> byChange(Change.Id changeId);

  ImmutableSet<Change.Id> byAccountId(Account.Id accountId);

  ImmutableSet<Change.Id> byAccountId(Account.Id accountId, boolean skipInvalidChanges);

  void star(Account.Id accountId, Change.Id changeId);

  void unstar(Account.Id accountId, Change.Id changeId);

  /**
   * Unstar the given change for all users.
   *
   * <p>Intended for use only when we're about to delete a change. For that reason, the change is
   * not reindexed.
   *
   * @param changeId change ID.
   * @throws IOException if an error occurred.
   */
  void unstarAllForChangeDeletion(Change.Id changeId) throws IOException;
}
