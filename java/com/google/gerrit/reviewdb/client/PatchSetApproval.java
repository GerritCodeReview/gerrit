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

package com.google.gerrit.reviewdb.client;

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Objects;

/** An approval (or negative approval) on a patch set. */
public final class PatchSetApproval {
  public static Key key(PatchSet.Id patchSetId, Account.Id accountId, LabelId labelId) {
    return new AutoValue_PatchSetApproval_Key(patchSetId, accountId, labelId);
  }

  @AutoValue
  public abstract static class Key {
    public abstract PatchSet.Id patchSetId();

    public abstract Account.Id accountId();

    public abstract LabelId labelId();
  }

  protected Key key;

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
  protected short value;

  protected Timestamp granted;

  @Nullable protected String tag;

  /** Real user that made this approval on behalf of the user recorded in {@link Key#accountId}. */
  @Nullable protected Account.Id realAccountId;

  protected boolean postSubmit;

  // DELETED: id = 4 (changeOpen)
  // DELETED: id = 5 (changeSortKey)

  protected PatchSetApproval() {}

  public PatchSetApproval(PatchSetApproval.Key k, short v, Date ts) {
    key = k;
    setValue(v);
    setGranted(ts);
  }

  public PatchSetApproval(PatchSet.Id psId, PatchSetApproval src) {
    key = key(psId, src.getAccountId(), src.getLabelId());
    value = src.getValue();
    granted = src.granted;
    realAccountId = src.realAccountId;
    tag = src.tag;
    postSubmit = src.postSubmit;
  }

  public PatchSetApproval(PatchSetApproval src) {
    this(src.getPatchSetId(), src);
  }

  public PatchSetApproval.Key getKey() {
    return key;
  }

  public PatchSet.Id getPatchSetId() {
    return key.patchSetId();
  }

  public Account.Id getAccountId() {
    return key.accountId();
  }

  public Account.Id getRealAccountId() {
    return realAccountId != null ? realAccountId : getAccountId();
  }

  public void setRealAccountId(Account.Id id) {
    // Use null for same real author, as before the column was added.
    realAccountId = Objects.equals(getAccountId(), id) ? null : id;
  }

  public LabelId getLabelId() {
    return key.labelId();
  }

  public short getValue() {
    return value;
  }

  public void setValue(short v) {
    value = v;
  }

  public Timestamp getGranted() {
    return granted;
  }

  public void setGranted(Date when) {
    if (when instanceof Timestamp) {
      granted = (Timestamp) when;
    } else {
      granted = new Timestamp(when.getTime());
    }
  }

  public void setTag(String t) {
    tag = t;
  }

  public String getLabel() {
    return getLabelId().get();
  }

  public boolean isLegacySubmit() {
    return LabelId.LEGACY_SUBMIT_NAME.equals(getLabel());
  }

  public String getTag() {
    return tag;
  }

  public void setPostSubmit(boolean postSubmit) {
    this.postSubmit = postSubmit;
  }

  public boolean isPostSubmit() {
    return postSubmit;
  }

  @Override
  public String toString() {
    StringBuilder sb =
        new StringBuilder("[")
            .append(key)
            .append(": ")
            .append(value)
            .append(",tag:")
            .append(tag)
            .append(",realAccountId:")
            .append(realAccountId);
    if (postSubmit) {
      sb.append(",postSubmit");
    }
    return sb.append(']').toString();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof PatchSetApproval) {
      PatchSetApproval p = (PatchSetApproval) o;
      return Objects.equals(key, p.key)
          && Objects.equals(value, p.value)
          && Objects.equals(granted, p.granted)
          && Objects.equals(tag, p.tag)
          && Objects.equals(realAccountId, p.realAccountId)
          && postSubmit == p.postSubmit;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, value, granted, tag, realAccountId, postSubmit);
  }
}
