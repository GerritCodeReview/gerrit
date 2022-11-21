// Copyright (C) 2021 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.SubmissionId;
import com.google.gerrit.server.update.ChainedReceiveCommands;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Test;

public class OpenRepoTest extends AbstractChangeNotesTest {

  private final Optional<Integer> NO_UPDATES_AT_ALL = Optional.of(0);
  private final Optional<Integer> ONLY_ONE_UPDATE = Optional.of(1);
  private final Optional<Integer> ONLY_TWO_UPDATES = Optional.of(2);
  private final Optional<Integer> MAX_PATCH_SETS = Optional.empty();

  private FakeChainedReceiveCommands fakeChainedReceiveCommands;

  @Override
  public void setUpTestEnvironment() throws Exception {
    super.setUpTestEnvironment();
    fakeChainedReceiveCommands = new FakeChainedReceiveCommands(repo);
  }

  @Test
  public void throwExceptionWhenExceedingMaxUpdatesLimit() throws Exception {
    try (OpenRepo openRepo = openRepo()) {
      Change c = newChange();
      ChangeUpdate update = newUpdate(c, changeOwner);
      update.setStatus(Change.Status.NEW);

      ListMultimap<String, ChangeUpdate> changeUpdates =
          new ImmutableListMultimap.Builder<String, ChangeUpdate>().put("one", update).build();

      assertThrows(
          LimitExceededException.class,
          () -> openRepo.addUpdates(changeUpdates, NO_UPDATES_AT_ALL, MAX_PATCH_SETS));
    }
  }

  @Test
  public void allowExceedingLimitWhenAttentionSetUpdateOnly() throws Exception {
    try (OpenRepo openRepo = openRepo()) {
      Change c = newChange();
      ChangeUpdate update = newUpdate(c, changeOwner);
      update.setStatus(Change.Status.NEW);

      addToAttentionSet(update);

      ListMultimap<String, ChangeUpdate> changeUpdates =
          new ImmutableListMultimap.Builder<String, ChangeUpdate>().put("one", update).build();

      openRepo.addUpdates(changeUpdates, NO_UPDATES_AT_ALL, MAX_PATCH_SETS);

      assertThat(fakeChainedReceiveCommands.commands.size()).isEqualTo(1);
    }
  }

  @Test
  public void allowExceedingLimitWhenChangeIsSubmitted() throws Exception {
    try (OpenRepo openRepo = openRepo()) {
      Change c = newChange();
      ChangeUpdate update = newUpdate(c, changeOwner);
      update.merge(
          new SubmissionId(c),
          ImmutableList.of(
              submitRecord(
                  "NOT_READY",
                  null,
                  submitLabel("Verified", "OK", changeOwner.getAccountId()),
                  submitLabel("Alternative-Code-Review", "NEED", null))));

      ListMultimap<String, ChangeUpdate> changeUpdates =
          new ImmutableListMultimap.Builder<String, ChangeUpdate>().put("one", update).build();

      openRepo.addUpdates(changeUpdates, NO_UPDATES_AT_ALL, MAX_PATCH_SETS);

      assertThat(fakeChainedReceiveCommands.commands.size()).isEqualTo(1);
    }
  }

  @Test
  public void allowExceedingLimitWhenChangeIsAbandoned() throws Exception {
    try (OpenRepo openRepo = openRepo()) {
      Change c = newChange();
      ChangeUpdate update = newUpdate(c, changeOwner);
      update.setStatus(Change.Status.ABANDONED);

      ListMultimap<String, ChangeUpdate> changeUpdates =
          new ImmutableListMultimap.Builder<String, ChangeUpdate>().put("one", update).build();

      openRepo.addUpdates(changeUpdates, NO_UPDATES_AT_ALL, MAX_PATCH_SETS);

      assertThat(fakeChainedReceiveCommands.commands.size()).isEqualTo(1);
    }
  }

  @Test
  public void attentionSetUpdateShouldNotContributeToOperationsCount() throws Exception {
    try (OpenRepo openRepo = openRepo()) {
      Change c1 = newChange();

      ChangeUpdate update1 = newUpdateForNewChange(c1, changeOwner);
      addToAttentionSet(update1);

      ChangeUpdate update2 = newUpdateForNewChange(c1, changeOwner);
      update2.setStatus(Change.Status.NEW);

      ListMultimap<String, ChangeUpdate> changeUpdates =
          new ImmutableListMultimap.Builder<String, ChangeUpdate>().put("two", update2).build();

      openRepo.addUpdates(changeUpdates, ONLY_TWO_UPDATES, MAX_PATCH_SETS);

      assertThat(fakeChainedReceiveCommands.commands.size()).isEqualTo(1);
    }
  }

  @Test
  public void attentionSetAndReviewerUpdateShouldContributeToOperationsCount() throws Exception {
    try (OpenRepo openRepo = openRepo()) {
      Change c1 = newChange();

      ChangeUpdate update1 = newUpdateForNewChange(c1, changeOwner);
      addToAttentionSet(update1);
      update1.putReviewer(otherUserId, ReviewerStateInternal.REVIEWER);

      ChangeUpdate update2 = newUpdateForNewChange(c1, changeOwner);
      update2.setStatus(Change.Status.NEW);

      ListMultimap<String, ChangeUpdate> changeUpdates =
          new ImmutableListMultimap.Builder<String, ChangeUpdate>().put("two", update2).build();

      assertThrows(
          LimitExceededException.class,
          () -> openRepo.addUpdates(changeUpdates, NO_UPDATES_AT_ALL, MAX_PATCH_SETS));
    }
  }

  @Test
  public void attentionSetAndReviewerByEmailUpdateShouldContributeToOperationsCount()
      throws Exception {
    try (OpenRepo openRepo = openRepo()) {
      Change c1 = newChange();

      ChangeUpdate update1 = newUpdateForNewChange(c1, changeOwner);
      addToAttentionSet(update1);
      update1.putReviewerByEmail(
          Address.create("anyEmail@mail.com"), ReviewerStateInternal.REVIEWER);

      ChangeUpdate update2 = newUpdateForNewChange(c1, changeOwner);
      update2.setStatus(Change.Status.NEW);

      ListMultimap<String, ChangeUpdate> changeUpdates =
          new ImmutableListMultimap.Builder<String, ChangeUpdate>().put("two", update2).build();

      assertThrows(
          LimitExceededException.class,
          () -> openRepo.addUpdates(changeUpdates, NO_UPDATES_AT_ALL, MAX_PATCH_SETS));
    }
  }

  @Test
  public void attentionSetAndWIPUpdateToTrueShouldContributeToOperationsCount() throws Exception {
    try (OpenRepo openRepo = openRepo()) {
      Change c1 = newChange();

      ChangeUpdate update1 = newUpdateForNewChange(c1, changeOwner);
      addToAttentionSet(update1);
      update1.setWorkInProgress(true);

      ChangeUpdate update2 = newUpdateForNewChange(c1, changeOwner);
      update2.setStatus(Change.Status.NEW);

      ListMultimap<String, ChangeUpdate> changeUpdates =
          new ImmutableListMultimap.Builder<String, ChangeUpdate>().put("two", update2).build();

      assertThrows(
          LimitExceededException.class,
          () -> openRepo.addUpdates(changeUpdates, NO_UPDATES_AT_ALL, MAX_PATCH_SETS));
    }
  }

  @Test
  public void attentionSetAndWIPUpdateToFalseShouldContributeToOperationsCount() throws Exception {
    try (OpenRepo openRepo = openRepo()) {
      Change c1 = newChange();

      ChangeUpdate update1 = newUpdateForNewChange(c1, changeOwner);
      addToAttentionSet(update1);
      update1.setWorkInProgress(false);

      ChangeUpdate update2 = newUpdateForNewChange(c1, changeOwner);
      update2.setStatus(Change.Status.NEW);

      ListMultimap<String, ChangeUpdate> changeUpdates =
          new ImmutableListMultimap.Builder<String, ChangeUpdate>().put("two", update2).build();

      assertThrows(
          LimitExceededException.class,
          () -> openRepo.addUpdates(changeUpdates, NO_UPDATES_AT_ALL, MAX_PATCH_SETS));
    }
  }

  @Test
  public void normalChangeShouldContributeToOperationsCount() throws Exception {
    try (OpenRepo openRepo = openRepo()) {
      Change c1 = newChange();

      ChangeUpdate update2 = newUpdateForNewChange(c1, changeOwner);
      update2.setStatus(Change.Status.NEW);

      ListMultimap<String, ChangeUpdate> changeUpdates =
          new ImmutableListMultimap.Builder<String, ChangeUpdate>().put("two", update2).build();

      assertThrows(
          LimitExceededException.class,
          () -> openRepo.addUpdates(changeUpdates, ONLY_ONE_UPDATE, MAX_PATCH_SETS));
    }
  }

  @Test
  public void canCreateChangeNotesFromOpenRepoAndChangeid() throws Exception {
    try (OpenRepo openRepo = openRepo()) {
      Change change = newChange();

      ChangeNotes changeNotes =
          changeNotesFactory.createChecked(openRepo.repo, project, change.getId(), null);

      assertThat(changeNotes).isNotNull();
      assertThat(changeNotes.getChangeId()).isEqualTo(change.getId());
    }
  }

  private void addToAttentionSet(ChangeUpdate update) {
    AttentionSetUpdate attentionSetUpdate =
        AttentionSetUpdate.createForWrite(
            otherUser.getAccountId(), AttentionSetUpdate.Operation.ADD, "test");
    update.addToPlannedAttentionSetUpdates(ImmutableSet.of(attentionSetUpdate));
  }

  private static class FakeChainedReceiveCommands extends ChainedReceiveCommands {
    Map<String, ReceiveCommand> commands = new HashMap<>();

    public FakeChainedReceiveCommands(Repository repo) {
      super(repo);
    }

    @Override
    public void add(ReceiveCommand cmd) {
      commands.put(cmd.getRefName(), cmd);
    }
  }

  private OpenRepo openRepo() {
    return new OpenRepo(repo, rw, null, fakeChainedReceiveCommands, false);
  }
}
