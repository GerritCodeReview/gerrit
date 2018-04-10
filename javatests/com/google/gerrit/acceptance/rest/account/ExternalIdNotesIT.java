// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.account;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.hamcrest.CoreMatchers.instanceOf;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class ExternalIdNotesIT extends AbstractDaemonTest {
  @Inject private ExternalIds externalIds;
  @Inject private ExternalIdNotes.Factory externalIdNotesFactory;

  @Test
  public void cannotAddExternalIdsWithSameEmail() throws Exception {
    String email = "foo@example.com";
    ExternalId extId1 = ExternalId.create(ExternalId.SCHEME_EXTERNAL, "abc", user.id, email, null);
    ExternalId extId2 = ExternalId.create(ExternalId.SCHEME_EXTERNAL, "xyz", user.id, email, null);

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.insert(extId1);
      extIdNotes.insert(extId2);

      expectException(
          "cannot assign email "
              + email
              + " to multiple external IDs: ["
              + extId1.key().get()
              + ", "
              + extId2.key().get()
              + "]");
      extIdNotes.commit(md);
    }
  }

  @Test
  public void cannotAddExternalIdWithEmailThatIsAlreadyUsed() throws Exception {
    String email = "foo@example.com";
    ExternalId extId1 = ExternalId.create(ExternalId.SCHEME_EXTERNAL, "abc", user.id, email, null);

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.insert(extId1);
      extIdNotes.commit(md);
    }

    ExternalId extId2 = ExternalId.create(ExternalId.SCHEME_EXTERNAL, "xyz", user.id, email, null);
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.insert(extId2);

      expectException(
          "cannot assign email "
              + email
              + " to external ID(s) ["
              + extId2.key().get()
              + "],"
              + " it is already assigned to external ID(s) ["
              + extId1.key().get()
              + "]");
      extIdNotes.commit(md);
    }
  }

  @Test
  public void canAssignExistingEmailToNewExternalId() throws Exception {
    String email = "foo@example.com";
    ExternalId extId1 = ExternalId.create(ExternalId.SCHEME_EXTERNAL, "abc", user.id, email, null);

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.insert(extId1);
      extIdNotes.commit(md);
    }

    ExternalId extId2 = ExternalId.create(ExternalId.SCHEME_EXTERNAL, "xyz", user.id, email, null);
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.upsert(ExternalId.create(extId1.key(), user.id, null, null));
      extIdNotes.insert(extId2);
      extIdNotes.commit(md);
    }

    assertExternalIdWithoutEmail(extId1.key());
    assertExternalId(extId2.key(), email);
  }

  @Test
  public void canAssignExistingEmailToDifferentExternalId() throws Exception {
    String email = "foo@example.com";
    ExternalId extId1 = ExternalId.create(ExternalId.SCHEME_EXTERNAL, "abc", user.id, email, null);
    ExternalId extId2 = ExternalId.create(ExternalId.SCHEME_EXTERNAL, "xyz", user.id, null, null);

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.insert(extId1);
      extIdNotes.insert(extId2);
      extIdNotes.commit(md);
    }

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.upsert(ExternalId.create(extId1.key(), user.id, null, null));
      extIdNotes.upsert(ExternalId.create(extId2.key(), user.id, email, null));
      extIdNotes.commit(md);
    }

    assertExternalIdWithoutEmail(extId1.key());
    assertExternalId(extId2.key(), email);
  }

  @Test
  public void cannotAssignExistingEmailToMultipleNewExternalIds() throws Exception {
    String email = "foo@example.com";
    ExternalId extId1 = ExternalId.create(ExternalId.SCHEME_EXTERNAL, "abc", user.id, email, null);

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.insert(extId1);
      extIdNotes.commit(md);
    }

    ExternalId extId2 = ExternalId.create(ExternalId.SCHEME_EXTERNAL, "efg", user.id, email, null);
    ExternalId extId3 = ExternalId.create(ExternalId.SCHEME_EXTERNAL, "hij", user.id, email, null);
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.upsert(ExternalId.create(extId1.key(), user.id, null, null));
      extIdNotes.insert(extId2);
      extIdNotes.insert(extId3);

      expectException(
          "cannot assign email "
              + email
              + " to multiple external IDs: ["
              + extId2.key().get()
              + ", "
              + extId3.key().get()
              + "]");
      extIdNotes.commit(md);
    }
  }

  @Test
  public void canUpdateExternalIdsIfDuplicateEmailsAlreadyExist() throws Exception {
    String email = "foo@example.com";
    ExternalId extId1 = ExternalId.create(ExternalId.SCHEME_EXTERNAL, "abc", user.id, email, null);
    ExternalId extId2 = ExternalId.create(ExternalId.SCHEME_EXTERNAL, "efg", user.id, email, null);

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes =
          externalIdNotesFactory.load(allUsersRepo).setDisableCheckForNewDuplicateEmails(true);
      extIdNotes.insert(extId1);
      extIdNotes.insert(extId2);
      extIdNotes.commit(md);
    }

    String email2 = "bar@example.com";
    ExternalId extId3 = ExternalId.create(ExternalId.SCHEME_EXTERNAL, "hij", user.id, email2, null);
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.upsert(ExternalId.create(extId1.key(), admin.id, email, null));
      extIdNotes.insert(extId3);
      extIdNotes.commit(md);
    }

    assertExternalId(extId1.key(), email);
    assertExternalId(extId2.key(), email);
    assertExternalId(extId3.key(), email2);
  }

  @Test
  public void cannotAddExistingDuplicateEmailToAnotherExternalId() throws Exception {
    String email = "foo@example.com";
    ExternalId extId1 = ExternalId.create(ExternalId.SCHEME_EXTERNAL, "abc", user.id, email, null);
    ExternalId extId2 = ExternalId.create(ExternalId.SCHEME_EXTERNAL, "efg", user.id, email, null);

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes =
          externalIdNotesFactory.load(allUsersRepo).setDisableCheckForNewDuplicateEmails(true);
      extIdNotes.insert(extId1);
      extIdNotes.insert(extId2);
      extIdNotes.commit(md);
    }

    ExternalId extId3 = ExternalId.create(ExternalId.SCHEME_EXTERNAL, "hij", user.id, email, null);
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.insert(extId3);

      expectException(
          "cannot assign email "
              + email
              + " to external ID(s) ["
              + extId3.key().get()
              + "],"
              + " it is already assigned to external ID(s) ["
              + extId1.key().get()
              + ", "
              + extId2.key().get()
              + "]");
      extIdNotes.commit(md);
    }
  }

  @Test
  public void canRemoveExistingDuplicateEmail() throws Exception {
    String email = "foo@example.com";
    ExternalId extId1 = ExternalId.create(ExternalId.SCHEME_EXTERNAL, "abc", user.id, email, null);
    ExternalId extId2 = ExternalId.create(ExternalId.SCHEME_EXTERNAL, "efg", user.id, email, null);

    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes =
          externalIdNotesFactory.load(allUsersRepo).setDisableCheckForNewDuplicateEmails(true);
      extIdNotes.insert(extId1);
      extIdNotes.insert(extId2);
      extIdNotes.commit(md);
    }

    String email2 = "bar@example.com";
    try (Repository allUsersRepo = repoManager.openRepository(allUsers);
        MetaDataUpdate md = metaDataUpdateFactory.create(allUsers)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(allUsersRepo);
      extIdNotes.upsert(ExternalId.create(extId2.key(), user.id, email2, null));
      extIdNotes.commit(md);
    }

    assertExternalId(extId1.key(), email);
    assertExternalId(extId2.key(), email2);
  }

  private void assertExternalIdWithoutEmail(ExternalId.Key extIdKey) throws Exception {
    assertExternalId(extIdKey, null);
  }

  private void assertExternalId(ExternalId.Key extIdKey, @Nullable String expectedEmail)
      throws Exception {
    Optional<ExternalId> extId = externalIds.get(extIdKey);
    assertThat(extId).named(extIdKey.get()).isPresent();
    assertThat(extId.get().email()).named("email of " + extIdKey.get()).isEqualTo(expectedEmail);
  }

  private void expectException(String message) {
    exception.expect(IOException.class);
    exception.expectCause(instanceOf(ConfigInvalidException.class));
    exception.expectMessage("Ambiguous emails:");
    exception.expectMessage(message);
  }
}
