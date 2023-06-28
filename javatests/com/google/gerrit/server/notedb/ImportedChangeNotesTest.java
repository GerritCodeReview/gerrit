// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.CommentRange;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdCache;
import com.google.gerrit.server.git.RepositoryCaseMismatchException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.AbstractModule;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

public class ImportedChangeNotesTest extends AbstractChangeNotesTest {

  private static final String FOREIGN_SERVER_ID = "foreign-server-id";
  private static final String IMPORTED_SERVER_ID = "gerrit-imported-1";

  private ExternalIdCache externalIdCacheMock;

  @Before
  @Override
  public void setUpTestEnvironment() throws Exception {
    setupTestPrerequisites();
  }

  private void initServerIds(String serverId, String... importedServerIds)
      throws Exception, RepositoryCaseMismatchException, RepositoryNotFoundException {
    externalIdCacheMock = mock(ExternalIdCache.class);
    injector =
        createTestInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(ExternalIdCache.class).toInstance(externalIdCacheMock);
              }
            },
            serverId,
            importedServerIds);
    injector.injectMembers(this);
    createAllUsers(injector);
  }

  @Test
  public void allowChangeFromImportedServerId() throws Exception {
    initServerIds(LOCAL_SERVER_ID, IMPORTED_SERVER_ID);

    linkImportedAccountTo(changeOwner.getAccountId(), Optional.of(changeOwner.getAccountId()));

    Change importedChange = newChange(createTestInjector(IMPORTED_SERVER_ID), false);
    Change localChange = newChange();

    assertThat(newNotes(importedChange).getServerId()).isEqualTo(IMPORTED_SERVER_ID);
    assertThat(newNotes(localChange).getServerId()).isEqualTo(LOCAL_SERVER_ID);
  }

  @Test
  public void rejectChangeWithForeignServerId() throws Exception {
    initServerIds(LOCAL_SERVER_ID);

    linkImportedAccountTo(changeOwner.getAccountId(), Optional.empty());

    Change foreignChange = newChange(createTestInjector(FOREIGN_SERVER_ID), false);

    InvalidServerIdException invalidServerIdEx =
        assertThrows(InvalidServerIdException.class, () -> newNotes(foreignChange));

    String invalidServerIdMessage = invalidServerIdEx.getMessage();
    assertThat(invalidServerIdMessage).contains("expected " + LOCAL_SERVER_ID);
    assertThat(invalidServerIdMessage).contains("actual: " + FOREIGN_SERVER_ID);
  }

  @Test
  public void changeFromImportedServerIdWithUnknownAccountId() throws Exception {
    initServerIds(LOCAL_SERVER_ID, IMPORTED_SERVER_ID);

    when(externalIdCacheMock.byKey(any())).thenReturn(Optional.empty());

    Change importedChange = newChange(createTestInjector(IMPORTED_SERVER_ID), false);
    assertThat(newNotes(importedChange).getServerId()).isEqualTo(IMPORTED_SERVER_ID);

    assertThat(newNotes(importedChange).getChange().getOwner())
        .isEqualTo(Account.UNKNOWN_ACCOUNT_ID);
  }

  @Test
  public void commentChangeFromImportedServerIdWithAnExistingAccountId() throws Exception {
    initServerIds(LOCAL_SERVER_ID, IMPORTED_SERVER_ID);

    Account.Id importedAccountId = Account.id(12345);
    linkImportedAccountTo(importedAccountId, Optional.of(otherUser.getAccountId()));
    IdentifiedUser importedUserIdentity = userFactory.create(importedAccountId);

    Change importedChange = newChange(createTestInjector(IMPORTED_SERVER_ID), false);
    ChangeUpdate update = newUpdate(importedChange, importedUserIdentity);
    update.putComment(
        HumanComment.Status.PUBLISHED,
        comment(importedChange, importedUserIdentity, IMPORTED_SERVER_ID));
    update.commit();

    ChangeNotes notes = newNotes(importedChange);

    ImmutableListMultimap<ObjectId, HumanComment> comments = notes.getHumanComments();
    assertThat(comments).hasSize(1);
    HumanComment gotComment = comments.entries().asList().get(0).getValue();
    assertThat(gotComment.author.getId()).isEqualTo(otherUser.getAccountId());
  }

  @Test
  public void commentChangeFromImportedServerIdWithUnknownAccountId() throws Exception {
    initServerIds(LOCAL_SERVER_ID, IMPORTED_SERVER_ID);

    Account.Id importedAccountId = Account.id(12345);
    linkImportedAccountTo(importedAccountId, Optional.empty());
    IdentifiedUser importedUserIdentity = userFactory.create(importedAccountId);

    Change importedChange = newChange(createTestInjector(IMPORTED_SERVER_ID), false);
    ChangeUpdate update = newUpdate(importedChange, importedUserIdentity);
    update.putComment(
        HumanComment.Status.PUBLISHED,
        comment(importedChange, importedUserIdentity, IMPORTED_SERVER_ID));
    update.commit();

    ChangeNotes notes = newNotes(importedChange);

    ImmutableListMultimap<ObjectId, HumanComment> comments = notes.getHumanComments();
    assertThat(comments).hasSize(1);
    HumanComment gotComment = comments.entries().asList().get(0).getValue();
    assertThat(gotComment.author.getId()).isEqualTo(Account.UNKNOWN_ACCOUNT_ID);
  }

  private HumanComment comment(Change change, IdentifiedUser commenter, String serverId) {
    return newComment(
        Objects.requireNonNull(change.currentPatchSetId()),
        "a.txt",
        "uuid1",
        new CommentRange(1, 2, 3, 4),
        1,
        commenter,
        null,
        TimeUtil.now(),
        "Comment",
        (short) 1,
        ObjectId.zeroId(),
        false,
        serverId);
  }

  private void linkImportedAccountTo(Account.Id importedAccount, Optional<Account.Id> localAccount)
      throws IOException {

    if (localAccount.isEmpty()) {
      when(externalIdCacheMock.byKey(any())).thenReturn(Optional.empty());
    } else {
      Account.Id localAccountId = localAccount.get();
      ExternalId.Key importedAccountIdKey =
          ExternalId.Key.create(
              ExternalId.SCHEME_IMPORTED, importedAccount + "@" + IMPORTED_SERVER_ID, false);
      ExternalId linkedExternalId =
          ExternalId.create(importedAccountIdKey, localAccountId, null, null, null);

      when(externalIdCacheMock.byKey(eq(importedAccountIdKey)))
          .thenReturn(Optional.of(linkedExternalId));
    }
  }
}
