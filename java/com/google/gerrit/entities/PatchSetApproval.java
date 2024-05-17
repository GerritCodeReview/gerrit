// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;
import com.google.common.primitives.Shorts;
import com.google.gerrit.common.ConvertibleToProto;
import java.time.Instant;
import java.util.Optional;

/** An approval (or negative approval) on a patch set. */
@AutoValue
public abstract class PatchSetApproval {
  public static Key key(PatchSet.Id patchSetId, Account.Id accountId, LabelId labelId) {
    return new AutoValue_PatchSetApproval_Key(patchSetId, accountId, labelId);
  }

  @AutoValue
  @ConvertibleToProto
  public abstract static class Key {
    public abstract PatchSet.Id patchSetId();

    public abstract Account.Id accountId();

    public abstract LabelId labelId();

    public boolean isLegacySubmit() {
      return LabelId.LEGACY_SUBMIT_NAME.equals(labelId().get());
    }
  }

  /**
   * Globally unique identifier.
   *
   * <p>The identifier is unique to each granted approval, i.e. approvals, re-added within same
   * {@link Change} or even {@link PatchSet} have different {@link UUID}.
   */
  @AutoValue
  public abstract static class UUID implements Comparable<UUID> {

    abstract String uuid();

    public String get() {
      return uuid();
    }

    @Override
    public final int compareTo(UUID o) {
      return uuid().compareTo(o.uuid());
    }

    @Override
    public final String toString() {
      return get();
    }
  }

  public static UUID uuid(String n) {
    return new AutoValue_PatchSetApproval_UUID(n);
  }

  public static Builder builder() {
    return new AutoValue_PatchSetApproval.Builder().postSubmit(false).copied(false);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder key(Key key);

    public abstract Key key();

    /**
     * {@link UUID} of {@link PatchSetApproval}.
     *
     * <p>Optional, since it might be missing for approvals, granted (persisted in NoteDB), before
     * {@link UUID} was introduced and does not apply to removals ( represented as approval with
     * {@link #value}, set to '0').
     */
    public abstract Builder uuid(Optional<UUID> uuid);

    public abstract Builder uuid(UUID uuid);

    public abstract Builder value(short value);

    public Builder value(int value) {
      return value(Shorts.checkedCast(value));
    }

    public abstract Builder granted(Instant granted);

    public abstract Builder tag(String tag);

    public abstract Builder tag(Optional<String> tag);

    public abstract Builder realAccountId(Account.Id realAccountId);

    abstract Optional<Account.Id> realAccountId();

    public abstract Builder postSubmit(boolean isPostSubmit);

    public abstract Builder copied(boolean isCopied);

    abstract PatchSetApproval autoBuild();

    public PatchSetApproval build() {
      if (!realAccountId().isPresent()) {
        realAccountId(key().accountId());
      }
      return autoBuild();
    }
  }

  public abstract Key key();

  public abstract Optional<UUID> uuid();

  /**
   * Value assigned by the user.
   *
   * <p>The precise meaning of "value" is up to each category.
   *
   * <p>In general:
   *
   * <ul>
   *   <li><b>&lt; 0:</b> The approval is rejected/revoked.
   *   <li><b>= 0:</b> No indication either way is provided.
   *   <li><b>&gt; 0:</b> The approval is approved/positive.
   * </ul>
   *
   * and in the negative and positive direction a magnitude can be assumed.The further from 0 the
   * more assertive the approval.
   */
  public abstract short value();

  public abstract Instant granted();

  public abstract Optional<String> tag();

  /** Real user that made this approval on behalf of the user recorded in {@link Key#accountId}. */
  public abstract Account.Id realAccountId();

  public abstract boolean postSubmit();

  public abstract boolean copied();

  public abstract Builder toBuilder();

  /**
   * Makes a copy of {@link PatchSetApproval} that applies to {@code psId}.
   *
   * <p>The returned {@link PatchSetApproval} has the same {@link UUID} as the original {@link
   * PatchSetApproval}, which is generated when it is originally granted.
   *
   * <p>This is needed since we want to keep the link between the original {@link PatchSetApproval}
   * and the {@link #copied} one.
   *
   * @param psId {@link PatchSet.Id} of {@link PatchSet} that the copy should be applied to.
   * @return {@link #copied} {@link PatchSetApproval} that applies to {@code psId}.
   */
  public PatchSetApproval copyWithPatchSet(PatchSet.Id psId) {
    return toBuilder()
        .key(key(psId, key().accountId(), key().labelId()))
        .uuid(uuid())
        .copied(true)
        .build();
  }

  public PatchSet.Id patchSetId() {
    return key().patchSetId();
  }

  public Account.Id accountId() {
    return key().accountId();
  }

  public LabelId labelId() {
    return key().labelId();
  }

  public String label() {
    return labelId().get();
  }

  public boolean isLegacySubmit() {
    return key().isLegacySubmit();
  }
}
