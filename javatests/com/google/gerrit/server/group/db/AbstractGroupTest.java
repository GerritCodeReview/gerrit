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

package com.google.gerrit.server.group.db;

import static com.google.gerrit.extensions.common.testing.CommitInfoSubject.assertThat;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.config.AllUsersNameProvider;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.TimeZone;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;

@Ignore
public class AbstractGroupTest extends GerritBaseTests {
  protected static final TimeZone TZ = TimeZone.getTimeZone("America/Los_Angeles");
  protected static final String SERVER_ID = "server-id";
  protected static final String SERVER_NAME = "Gerrit Server";
  protected static final String SERVER_EMAIL = "noreply@gerritcodereview.com";
  protected static final int SERVER_ACCOUNT_NUMBER = 100000;
  protected static final int USER_ACCOUNT_NUMBER = 100001;

  protected AllUsersName allUsersName;
  protected InMemoryRepositoryManager repoManager;
  protected Repository allUsersRepo;
  protected Account.Id serverAccountId;
  protected PersonIdent serverIdent;
  protected Account.Id userId;
  protected PersonIdent userIdent;

  @Before
  public void abstractGroupTestSetUp() throws Exception {
    allUsersName = new AllUsersName(AllUsersNameProvider.DEFAULT);
    repoManager = new InMemoryRepositoryManager();
    allUsersRepo = repoManager.createRepository(allUsersName);
    serverAccountId = new Account.Id(SERVER_ACCOUNT_NUMBER);
    serverIdent = new PersonIdent(SERVER_NAME, SERVER_EMAIL, TimeUtil.nowTs(), TZ);
    userId = new Account.Id(USER_ACCOUNT_NUMBER);
    userIdent = newPersonIdent(userId, serverIdent);
  }

  @After
  public void abstractGroupTestTearDown() throws Exception {
    allUsersRepo.close();
  }

  protected Timestamp getTipTimestamp(AccountGroup.UUID uuid) throws Exception {
    try (RevWalk rw = new RevWalk(allUsersRepo)) {
      Ref ref = allUsersRepo.exactRef(RefNames.refsGroups(uuid));
      return ref == null
          ? null
          : new Timestamp(rw.parseCommit(ref.getObjectId()).getAuthorIdent().getWhen().getTime());
    }
  }

  protected static void assertServerCommit(CommitInfo commitInfo, String expectedMessage) {
    assertCommit(commitInfo, expectedMessage, SERVER_NAME, SERVER_EMAIL);
  }

  protected static void assertCommit(
      CommitInfo commitInfo, String expectedMessage, String expectedName, String expectedEmail) {
    assertThat(commitInfo).message().isEqualTo(expectedMessage);
    assertThat(commitInfo).author().name().isEqualTo(expectedName);
    assertThat(commitInfo).author().email().isEqualTo(expectedEmail);

    // Committer should always be the server, regardless of author.
    assertThat(commitInfo).committer().name().isEqualTo(SERVER_NAME);
    assertThat(commitInfo).committer().email().isEqualTo(SERVER_EMAIL);
    assertThat(commitInfo).committer().date().isEqualTo(commitInfo.author.date);
    assertThat(commitInfo).committer().tz().isEqualTo(commitInfo.author.tz);
  }

  protected MetaDataUpdate createMetaDataUpdate(PersonIdent authorIdent) {
    MetaDataUpdate md =
        new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsersName, allUsersRepo);
    md.getCommitBuilder().setAuthor(authorIdent);
    md.getCommitBuilder().setCommitter(serverIdent); // Committer is always the server identity.
    return md;
  }

  protected static PersonIdent newPersonIdent() {
    return new PersonIdent(SERVER_NAME, SERVER_EMAIL, TimeUtil.nowTs(), TZ);
  }

  protected static PersonIdent newPersonIdent(Account.Id id, PersonIdent ident) {
    return new PersonIdent(
        getAccountName(id), getAccountEmail(id), ident.getWhen(), ident.getTimeZone());
  }

  protected AuditLogFormatter getAuditLogFormatter() {
    return AuditLogFormatter.create(
        AbstractGroupTest::getAccount, uuid -> getGroup(uuid), SERVER_ID);
  }

  private static Optional<Account> getAccount(Account.Id id) {
    Account account = new Account(id, TimeUtil.nowTs());
    account.setFullName("Account " + id);
    return Optional.of(account);
  }

  private Optional<GroupDescription.Basic> getGroup(AccountGroup.UUID uuid) {
    GroupDescription.Basic group =
        new GroupDescription.Basic() {
          @Override
          public AccountGroup.UUID getGroupUUID() {
            return uuid;
          }

          @Override
          public String getName() {
            try {
              return GroupConfig.loadForGroup(allUsersRepo, uuid)
                  .getLoadedGroup()
                  .map(InternalGroup::getName)
                  .orElse("Group " + uuid);
            } catch (IOException | ConfigInvalidException e) {
              return "Group " + uuid;
            }
          }

          @Nullable
          @Override
          public String getEmailAddress() {
            return null;
          }

          @Nullable
          @Override
          public String getUrl() {
            return null;
          }
        };
    return Optional.of(group);
  }

  protected static String getAccountName(Account.Id id) {
    return "Account " + id;
  }

  protected static String getAccountEmail(Account.Id id) {
    return String.format("%s@%s", id, SERVER_ID);
  }
}
