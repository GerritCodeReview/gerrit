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
import java.sql.Timestamp;
import java.util.Date;
import java.util.Optional;

/** An approval (or negative approval) on a patch set. */
@AutoValue
public abstract class PatchSetApproval {
  public static Key key(PatchSet.Id patchSetId, Account.Id accountId, LabelId labelId) {
    return new AutoValue_PatchSetApproval_Key(patchSetId, accountId, labelId);
  }

  @AutoValue
  public abstract static class Key {
    public abstract PatchSet.Id patchSetId();

    public abstract Account.Id accountId();

    public abstract LabelId labelId();

    public boolean isLegacySubmit() {
      return LabelId.LEGACY_SUBMIT_NAME.equals(labelId().get());
    }
  }

  public static Builder builder() {
    return new AutoValue_PatchSetApproval.Builder().postSubmit(false);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder key(Key key);

    public abstract Key key();

    public abstract Builder value(short value);

    public Builder value(int value) {
      return value(Shorts.checkedCast(value));
    }

    public abstract Builder granted(Timestamp granted);

    public Builder granted(Date granted) {
      return granted(new Timestamp(granted.getTime()));
    }

    public abstract Builder tag(String tag);

    public abstract Builder tag(Optional<String> tag);

    public abstract Builder realAccountId(Account.Id realAccountId);

    abstract Optional<Account.Id> realAccountId();

    public abstract Builder postSubmit(boolean isPostSubmit);

    abstract PatchSetApproval autoBuild();

    public PatchSetApproval build() {
      if (!realAccountId().isPresent()) {
        realAccountId(key().accountId());
      }
      return autoBuild();
    }
  }

  public abstract Key key();

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

  public abstract Timestamp granted();

  public abstract Optional<String> tag();

  /** Real user that made this approval on behalf of the user recorded in {@link Key#accountId}. */
  public abstract Account.Id realAccountId();

  public abstract boolean postSubmit();

  public abstract Builder toBuilder();

  public PatchSetApproval copyWithPatchSet(PatchSet.Id psId) {
    return toBuilder().key(key(psId, key().accountId(), key().labelId())).build();
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
