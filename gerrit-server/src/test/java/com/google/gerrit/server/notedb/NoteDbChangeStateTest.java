// Copyright (C) 2016 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage.NOTE_DB;
import static com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage.REVIEW_DB;
import static com.google.gerrit.server.notedb.NoteDbChangeState.applyDelta;
import static com.google.gerrit.server.notedb.NoteDbChangeState.parse;
import static org.eclipse.jgit.lib.ObjectId.zeroId;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.notedb.NoteDbChangeState.Delta;
import com.google.gerrit.testutil.TestChanges;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

/** Unit tests for {@link NoteDbChangeState}. */
public class NoteDbChangeStateTest {
  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  ObjectId SHA1 = ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
  ObjectId SHA2 = ObjectId.fromString("abcd1234abcd1234abcd1234abcd1234abcd1234");
  ObjectId SHA3 = ObjectId.fromString("badc0feebadc0feebadc0feebadc0feebadc0fee");

  @Test
  public void parseReviewDbWithoutDrafts() {
    NoteDbChangeState state = parse(new Change.Id(1), SHA1.name());
    assertThat(state.getPrimaryStorage()).isEqualTo(REVIEW_DB);
    assertThat(state.getChangeId()).isEqualTo(new Change.Id(1));
    assertThat(state.getChangeMetaId()).isEqualTo(SHA1);
    assertThat(state.getDraftIds()).isEmpty();
    assertThat(state.toString()).isEqualTo(SHA1.name());

    state = parse(new Change.Id(1), "R," + SHA1.name());
    assertThat(state.getPrimaryStorage()).isEqualTo(REVIEW_DB);
    assertThat(state.getChangeId()).isEqualTo(new Change.Id(1));
    assertThat(state.getChangeMetaId()).isEqualTo(SHA1);
    assertThat(state.getDraftIds()).isEmpty();
    assertThat(state.toString()).isEqualTo(SHA1.name());
  }

  @Test
  public void parseReviewDbWithDrafts() {
    String str = SHA1.name() + ",2003=" + SHA2.name() + ",1001=" + SHA3.name();
    String expected = SHA1.name() + ",1001=" + SHA3.name() + ",2003=" + SHA2.name();
    NoteDbChangeState state = parse(new Change.Id(1), str);
    assertThat(state.getPrimaryStorage()).isEqualTo(REVIEW_DB);
    assertThat(state.getChangeId()).isEqualTo(new Change.Id(1));
    assertThat(state.getChangeMetaId()).isEqualTo(SHA1);
    assertThat(state.getDraftIds())
        .containsExactly(
            new Account.Id(1001), SHA3,
            new Account.Id(2003), SHA2);
    assertThat(state.toString()).isEqualTo(expected);

    state = parse(new Change.Id(1), "R," + str);
    assertThat(state.getPrimaryStorage()).isEqualTo(REVIEW_DB);
    assertThat(state.getChangeId()).isEqualTo(new Change.Id(1));
    assertThat(state.getChangeMetaId()).isEqualTo(SHA1);
    assertThat(state.getDraftIds())
        .containsExactly(
            new Account.Id(1001), SHA3,
            new Account.Id(2003), SHA2);
    assertThat(state.toString()).isEqualTo(expected);
  }

  @Test
  public void applyDeltaToNullWithNoNewMetaId() {
    Change c = newChange();
    assertThat(c.getNoteDbState()).isNull();
    applyDelta(c, Delta.create(c.getId(), noMetaId(), noDrafts()));
    assertThat(c.getNoteDbState()).isNull();

    applyDelta(c, Delta.create(c.getId(), noMetaId(), drafts(new Account.Id(1001), zeroId())));
    assertThat(c.getNoteDbState()).isNull();
  }

  @Test
  public void applyDeltaToMetaId() {
    Change c = newChange();
    applyDelta(c, Delta.create(c.getId(), metaId(SHA1), noDrafts()));
    assertThat(c.getNoteDbState()).isEqualTo(SHA1.name());

    applyDelta(c, Delta.create(c.getId(), metaId(SHA2), noDrafts()));
    assertThat(c.getNoteDbState()).isEqualTo(SHA2.name());

    // No-op delta.
    applyDelta(c, Delta.create(c.getId(), noMetaId(), noDrafts()));
    assertThat(c.getNoteDbState()).isEqualTo(SHA2.name());

    // Set to zero clears the field.
    applyDelta(c, Delta.create(c.getId(), metaId(zeroId()), noDrafts()));
    assertThat(c.getNoteDbState()).isNull();
  }

  @Test
  public void applyDeltaToDrafts() {
    Change c = newChange();
    applyDelta(c, Delta.create(c.getId(), metaId(SHA1), drafts(new Account.Id(1001), SHA2)));
    assertThat(c.getNoteDbState()).isEqualTo(SHA1.name() + ",1001=" + SHA2.name());

    applyDelta(c, Delta.create(c.getId(), noMetaId(), drafts(new Account.Id(2003), SHA3)));
    assertThat(c.getNoteDbState())
        .isEqualTo(SHA1.name() + ",1001=" + SHA2.name() + ",2003=" + SHA3.name());

    applyDelta(c, Delta.create(c.getId(), noMetaId(), drafts(new Account.Id(2003), zeroId())));
    assertThat(c.getNoteDbState()).isEqualTo(SHA1.name() + ",1001=" + SHA2.name());

    applyDelta(c, Delta.create(c.getId(), metaId(SHA3), noDrafts()));
    assertThat(c.getNoteDbState()).isEqualTo(SHA3.name() + ",1001=" + SHA2.name());
  }

  @Test
  public void parseNoteDbPrimary() {
    NoteDbChangeState state = parse(new Change.Id(1), "N");
    assertThat(state.getPrimaryStorage()).isEqualTo(NOTE_DB);
    assertThat(state.getRefState().isPresent()).isFalse();
  }

  @Test(expected = IllegalArgumentException.class)
  public void parseInvalidPrimaryStorage() {
    parse(new Change.Id(1), "X");
  }

  @Test
  public void applyDeltaToNoteDbPrimaryIsNoOp() {
    Change c = newChange();
    c.setNoteDbState("N");
    applyDelta(c, Delta.create(c.getId(), metaId(SHA1), drafts(new Account.Id(1001), SHA2)));
    assertThat(c.getNoteDbState()).isEqualTo("N");
  }

  private static Change newChange() {
    return TestChanges.newChange(new Project.NameKey("project"), new Account.Id(12345));
  }

  // Static factory methods to avoid type arguments when using as method args.

  private static Optional<ObjectId> noMetaId() {
    return Optional.empty();
  }

  private static Optional<ObjectId> metaId(ObjectId id) {
    return Optional.of(id);
  }

  private static ImmutableMap<Account.Id, ObjectId> noDrafts() {
    return ImmutableMap.of();
  }

  private static ImmutableMap<Account.Id, ObjectId> drafts(Object... args) {
    checkArgument(args.length % 2 == 0);
    ImmutableMap.Builder<Account.Id, ObjectId> b = ImmutableMap.builder();
    for (int i = 0; i < args.length / 2; i++) {
      b.put((Account.Id) args[2 * i], (ObjectId) args[2 * i + 1]);
    }
    return b.build();
  }
}
