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

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.Change;
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
  private final Optional<Integer> MAX_PATCH_SETS = Optional.empty();

  @Test(expected = LimitExceededException.class)
  public void throwExceptionWhenExceedingMaxUpdatesLimit() throws Exception {

    FakeChainedReceiveCommands fakeChainedReceiveCommands = new FakeChainedReceiveCommands(repo);
    OpenRepo openRepo = new OpenRepo(repo, rw, null, fakeChainedReceiveCommands, false);

    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setStatus(Change.Status.NEW);
    update.commit();

    ListMultimap<String, ChangeUpdate> changeUpdates =
        new ImmutableListMultimap.Builder<String, ChangeUpdate>().put("one", update).build();

    openRepo.addUpdates(changeUpdates, NO_UPDATES_AT_ALL, MAX_PATCH_SETS);
  }

  @Test
  public void allowExceedingLimitWhenAttentionSetUpdateOnly() throws Exception {
    FakeChainedReceiveCommands fakeChainedReceiveCommands = new FakeChainedReceiveCommands(repo);
    OpenRepo openRepo = new OpenRepo(repo, rw, null, fakeChainedReceiveCommands, false);

    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setStatus(Change.Status.NEW);

    // Add to attention set
    AttentionSetUpdate attentionSetUpdate =
        AttentionSetUpdate.createForWrite(
            otherUser.getAccountId(), AttentionSetUpdate.Operation.ADD, "test");
    update.addToPlannedAttentionSetUpdates(ImmutableSet.of(attentionSetUpdate));

    update.commit();

    ListMultimap<String, ChangeUpdate> changeUpdates =
        new ImmutableListMultimap.Builder<String, ChangeUpdate>().put("one", update).build();

    openRepo.addUpdates(changeUpdates, NO_UPDATES_AT_ALL, MAX_PATCH_SETS);

    assertThat(fakeChainedReceiveCommands.commands.size()).isEqualTo(1);
  }

  @Test
  public void attentionSetUpdateShouldNotContributeToOperationsCount() throws Exception {
    FakeChainedReceiveCommands fakeChainedReceiveCommands = new FakeChainedReceiveCommands(repo);
    OpenRepo openRepo = new OpenRepo(repo, rw, null, fakeChainedReceiveCommands, false);

    // First change
    Change c1 = newChange();
    ChangeUpdate update1 = newUpdate(c1, changeOwner);
    update1.setStatus(Change.Status.NEW);

    // Add to attention set
    AttentionSetUpdate attentionSetUpdate =
        AttentionSetUpdate.createForWrite(
            otherUser.getAccountId(), AttentionSetUpdate.Operation.ADD, "test");
    update1.addToPlannedAttentionSetUpdates(ImmutableSet.of(attentionSetUpdate));

    update1.commit();

    // Second change
    Change c2 = newChange();
    ChangeUpdate update2 = newUpdate(c2, changeOwner);
    update2.setStatus(Change.Status.NEW);
    update2.commit();

    ListMultimap<String, ChangeUpdate> changeUpdates =
        new ImmutableListMultimap.Builder<String, ChangeUpdate>()
            .put("one", update1)
            .put("two", update2)
            .build();

    openRepo.addUpdates(changeUpdates, ONLY_ONE_UPDATE, MAX_PATCH_SETS);

    assertThat(fakeChainedReceiveCommands.commands.size()).isEqualTo(2);
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
}
