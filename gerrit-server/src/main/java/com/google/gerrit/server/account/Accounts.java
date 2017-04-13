// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/** Class to access accounts. */
@Singleton
public class Accounts {
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;

  @Inject
  Accounts(GitRepositoryManager repoManager, AllUsersName allUsersName) {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
  }

  /**
   * Returns the first n account IDs.
   *
   * @param n the number of account IDs that should be returned
   * @return first n account IDs
   */
  public List<Account.Id> firstNIds(int n) throws IOException {
    List<Account.Id> accountIds = new ArrayList<>();
    readUserRefsSortedByAccountId(
        r -> {
          accountIds.add(r.accountId());
          return accountIds.size() < n;
        });
    return accountIds;
  }

  /**
   * Checks if any account exists.
   *
   * @return {@code true} if at least one account exists, otherwise {@code false}
   */
  public boolean hasAnyAccount() throws IOException {
    AtomicBoolean hasAnyAccount = new AtomicBoolean(false);
    readUserRefs(
        r -> {
          hasAnyAccount.set(true);
          return false;
        });
    return hasAnyAccount.get();
  }

  private void readUserRefsSortedByAccountId(UserRefConsumer consumer) throws IOException {
    readUserRefs(true, consumer);
  }

  private void readUserRefs(UserRefConsumer consumer) throws IOException {
    readUserRefs(false, consumer);
  }

  private void readUserRefs(boolean sortByAccountId, UserRefConsumer consumer) throws IOException {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      Collection<Ref> userRefs = repo.getRefDatabase().getRefs(RefNames.REFS_USERS).values();
      Stream<UserRef> userRefStream =
          userRefs
              .stream()
              .filter(r -> !RefNames.REFS_USERS_DEFAULT.equals(r.getName()))
              .map(UserRef::create);
      if (sortByAccountId) {
        userRefStream = userRefStream.sorted((r1, r2) -> r1.compareTo(r2));
      }
      for (UserRef userRef : userRefStream.collect(toList())) {
        if (!consumer.accept(userRef)) {
          return;
        }
      }
    }
  }

  @FunctionalInterface
  private static interface UserRefConsumer {
    /**
     * Consumes a user ref.
     *
     * @param userRef user ref
     * @return whether further user refs should be read
     */
    boolean accept(UserRef userRef) throws IOException;
  }

  @AutoValue
  abstract static class UserRef implements Comparable<UserRef> {
    static UserRef create(Ref ref) {
      return new AutoValue_Accounts_UserRef(ref, Account.Id.fromRef(ref.getName()));
    }

    abstract Ref ref();

    abstract Account.Id accountId();

    @Override
    public int compareTo(UserRef other) {
      return accountId().get() - other.accountId().get();
    }
  }
}
