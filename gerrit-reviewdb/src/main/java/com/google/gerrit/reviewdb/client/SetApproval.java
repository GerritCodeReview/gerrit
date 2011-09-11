// Copyright (C) 2011 The Android Open Source Project
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

import java.sql.Timestamp;

/**
 * An abstract class to be extended by {@link PatchSetApproval}
 * and/or {@link ChangeSetApproval}
 * */
public abstract class SetApproval<T> {

  /**
   * Value assigned by the user.
   * <p>
   * The precise meaning of "value" is up to each category.
   * <p>
   * In general:
   * <ul>
   * <li><b>&lt; 0:</b> The approval is rejected/revoked.</li>
   * <li><b>= 0:</b> No indication either way is provided.</li>
   * <li><b>&gt; 0:</b> The approval is approved/positive.</li>
   * </ul>
   * and in the negative and positive direction a magnitude can be assumed.The
   * further from 0 the more assertive the approval.
   */
  @Column(id = 2)
  protected short value;

  @Column(id = 3)
  protected Timestamp granted;

  public abstract Account.Id getAccountId();

  public abstract ApprovalCategory.Id getCategoryId();

  public abstract T getSetId();

  public short getValue() {
    return value;
  }

  public void setValue(final short v) {
    value = v;
  }

  public Timestamp getGranted() {
    return granted;
  }

  public void setGranted() {
    granted = new Timestamp(System.currentTimeMillis());
  }
}