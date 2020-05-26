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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Iterables;
import com.google.common.collect.UnmodifiableIterator;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.extensions.api.changes.AddToAttentionSetInput;
import com.google.gerrit.extensions.api.changes.RemoveFromAttentionSetInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.server.util.time.TimeUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
@UseClockStep(clockStepUnit = TimeUnit.MINUTES)
public class AttentionSetIT extends AbstractDaemonTest {
  /** Simulates a fake clock. Uses second granularity. */
  private static class FakeClock implements LongSupplier {
    Instant now = Instant.now();

    @Override
    public long getAsLong() {
      return TimeUnit.SECONDS.toMillis(now.getEpochSecond());
    }

    Instant now() {
      return Instant.ofEpochSecond(now.getEpochSecond());
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }
  }

  private FakeClock fakeClock = new FakeClock();

  @Before
  public void setUp() {
    TimeUtil.setCurrentMillisSupplier(fakeClock);
  }

  @Test
  public void emptyAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(r.getChange().attentionSet()).isEmpty();
  }

  @Test
  public void addUser() throws Exception {
    PushOneCommit.Result r = createChange();
    int accountId =
        change(r).addToAttentionSet(new AddToAttentionSetInput(user.email(), "first"))._accountId;
    assertThat(accountId).isEqualTo(user.id().get());
    AttentionSetUpdate expectedAttentionSetUpdate =
        AttentionSetUpdate.createFromRead(
            fakeClock.now(), user.id(), AttentionSetUpdate.Operation.ADD, "first");
    assertThat(r.getChange().attentionSet()).containsExactly(expectedAttentionSetUpdate);

    // Second add is ignored.
    accountId =
        change(r).addToAttentionSet(new AddToAttentionSetInput(user.email(), "second"))._accountId;
    assertThat(accountId).isEqualTo(user.id().get());
    assertThat(r.getChange().attentionSet()).containsExactly(expectedAttentionSetUpdate);
  }

  @Test
  public void addMultipleUsers() throws Exception {
    PushOneCommit.Result r = createChange();
    Instant timestamp1 = fakeClock.now();
    int accountId1 =
        change(r).addToAttentionSet(new AddToAttentionSetInput(user.email(), "user"))._accountId;
    assertThat(accountId1).isEqualTo(user.id().get());
    fakeClock.advance(Duration.ofSeconds(42));
    Instant timestamp2 = fakeClock.now();
    int accountId2 =
        change(r)
            .addToAttentionSet(new AddToAttentionSetInput(admin.id().toString(), "admin"))
            ._accountId;
    assertThat(accountId2).isEqualTo(admin.id().get());

    AttentionSetUpdate expectedAttentionSetUpdate1 =
        AttentionSetUpdate.createFromRead(
            timestamp1, user.id(), AttentionSetUpdate.Operation.ADD, "user");
    AttentionSetUpdate expectedAttentionSetUpdate2 =
        AttentionSetUpdate.createFromRead(
            timestamp2, admin.id(), AttentionSetUpdate.Operation.ADD, "admin");
    assertThat(r.getChange().attentionSet())
        .containsExactly(expectedAttentionSetUpdate1, expectedAttentionSetUpdate2);
  }

  @Test
  public void removeUser() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).addToAttentionSet(new AddToAttentionSetInput(user.email(), "added"));
    fakeClock.advance(Duration.ofSeconds(42));
    change(r).attention(user.id().toString()).remove(new RemoveFromAttentionSetInput("removed"));
    AttentionSetUpdate expectedAttentionSetUpdate =
        AttentionSetUpdate.createFromRead(
            fakeClock.now(), user.id(), AttentionSetUpdate.Operation.REMOVE, "removed");
    assertThat(r.getChange().attentionSet()).containsExactly(expectedAttentionSetUpdate);

    // Second removal is ignored.
    fakeClock.advance(Duration.ofSeconds(42));
    change(r)
        .attention(user.id().toString())
        .remove(new RemoveFromAttentionSetInput("removed again"));
    assertThat(r.getChange().attentionSet()).containsExactly(expectedAttentionSetUpdate);
  }

  @Test
  public void removeUnrelatedUser() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).attention(user.id().toString()).remove(new RemoveFromAttentionSetInput("foo"));
    assertThat(r.getChange().attentionSet()).isEmpty();
  }

  @Test
  public void abandonRemovesUsers() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).addToAttentionSet(new AddToAttentionSetInput(user.email(), "user"));
    change(r).addToAttentionSet(new AddToAttentionSetInput(admin.email(), "admin"));

    change(r).abandon();

    UnmodifiableIterator<AttentionSetUpdate> attentionSets =
        r.getChange().attentionSet().iterator();
    AttentionSetUpdate userUpdate = attentionSets.next();
    assertThat(userUpdate.account()).isEqualTo(user.id());
    assertThat(userUpdate.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(userUpdate.reason()).isEqualTo("Change was abandoned");

    AttentionSetUpdate adminUpdate = attentionSets.next();
    assertThat(adminUpdate.account()).isEqualTo(admin.id());
    assertThat(adminUpdate.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(adminUpdate.reason()).isEqualTo("Change was abandoned");
  }

  @Test
  public void workInProgressRemovesUsers() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).addToAttentionSet(new AddToAttentionSetInput(user.email(), "reason"));

    change(r).setWorkInProgress();

    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("Change was marked work in progress");
  }

  @Test
  public void submitRemovesUsersForAllSubmittedChanges() throws Exception {
    PushOneCommit.Result r1 = createChange("refs/heads/master", "file1", "content");
    change(r1).addToAttentionSet(new AddToAttentionSetInput(user.email(), "reason"));
    change(r1).current().review(ReviewInput.approve());

    PushOneCommit.Result r2 = createChange("refs/heads/master", "file2", "content");

    change(r2).addToAttentionSet(new AddToAttentionSetInput(user.email(), "reason"));
    change(r2).current().review(ReviewInput.approve());

    change(r2).current().submit();

    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r1.getChange().attentionSet());
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("Change was submitted");

    attentionSet = Iterables.getOnlyElement(r2.getChange().attentionSet());
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("Change was submitted");
  }
}
