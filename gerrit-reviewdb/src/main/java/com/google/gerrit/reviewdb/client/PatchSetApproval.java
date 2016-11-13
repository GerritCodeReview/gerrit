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

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.CompoundKey;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Objects;

/** An approval (or negative approval) on a patch set. */
public final class PatchSetApproval {
  public static class Key extends CompoundKey<PatchSet.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1, name = Column.NONE)
    protected PatchSet.Id patchSetId;

    @Column(id = 2)
    protected Account.Id accountId;

    @Column(id = 3)
    protected LabelId categoryId;

    protected Key() {
      patchSetId = new PatchSet.Id();
      accountId = new Account.Id();
      categoryId = new LabelId();
    }

    public Key(final PatchSet.Id ps, final Account.Id a, final LabelId c) {
      this.patchSetId = ps;
      this.accountId = a;
      this.categoryId = c;
    }

    @Override
    public PatchSet.Id getParentKey() {
      return patchSetId;
    }

    public Account.Id getAccountId() {
      return accountId;
    }

    public LabelId getLabelId() {
      return categoryId;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {accountId, categoryId};
    }
  }

  @Column(id = 1, name = Column.NONE)
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
  @Column(id = 2)
  protected short value;

  @Column(id = 3)
  protected Timestamp granted;

  @Column(id = 6, notNull = false)
  protected String tag;

  /** Real user that made this approval on behalf of the user recorded in {@link Key#accountId}. */
  @Column(id = 7, notNull = false)
  protected Account.Id realAccountId;

  @Column(id = 8)
  protected boolean postSubmit;

  // DELETED: id = 4 (changeOpen)
  // DELETED: id = 5 (changeSortKey)

  protected PatchSetApproval() {}

  public PatchSetApproval(PatchSetApproval.Key k, short v, Date ts) {
    key = k;
    setValue(v);
    setGranted(ts);
  }

  public PatchSetApproval(final PatchSet.Id psId, final PatchSetApproval src) {
    key = new PatchSetApproval.Key(psId, src.getAccountId(), src.getLabelId());
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
    return key.patchSetId;
  }

  public Account.Id getAccountId() {
    return key.accountId;
  }

  public Account.Id getRealAccountId() {
    return realAccountId != null ? realAccountId : getAccountId();
  }

  public void setRealAccountId(Account.Id id) {
    // Use null for same real author, as before the column was added.
    realAccountId = Objects.equals(getAccountId(), id) ? null : id;
  }

  public LabelId getLabelId() {
    return key.categoryId;
  }

  public short getValue() {
    return value;
  }

  public void setValue(final short v) {
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
    return Objects.hash(key, value, granted, tag);
  }
}
