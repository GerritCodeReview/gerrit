// Copyright (C) 2014 The Android Open Source Project
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
import java.util.Objects;

/** A verification on a patch set. */
public class PatchSetVerification {

  public static class Key extends CompoundKey<PatchSet.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1, name = Column.NONE)
    protected PatchSet.Id patchSetId;

    @Column(id = 3)
    protected LabelId categoryId;

    protected Key() {
      patchSetId = new PatchSet.Id();
      categoryId = new LabelId();
    }

    public Key(PatchSet.Id ps, LabelId c) {
      this.patchSetId = ps;
      this.categoryId = c;
    }

    @Override
    public PatchSet.Id getParentKey() {
      return patchSetId;
    }

    public LabelId getLabelId() {
      return categoryId;
    }

    @Override
    public com.google.gwtorm.client.Key<?>[] members() {
      return new com.google.gwtorm.client.Key<?>[] {categoryId};
    }
  }

  @Column(id = 1, name = Column.NONE)
  protected Key key;

  @Column(id = 2)
  protected short value;

  @Column(id = 3)
  protected Timestamp granted;

  @Column(id = 4, notNull = false, length = 255)
  protected String url;

  @Column(id = 5, notNull = false, length = 255)
  protected String verifier;

  @Column(id = 6, notNull = false, length = 255)
  protected String comment;

  protected PatchSetVerification() {
  }

  public PatchSetVerification(PatchSetVerification.Key k, short v,
      Timestamp ts) {
    key = k;
    setValue(v);
    setGranted(ts);
  }

  public PatchSetVerification.Key getKey() {
    return key;
  }

  public PatchSet.Id getPatchSetId() {
    return key.patchSetId;
  }

  public LabelId getLabelId() {
    return key.categoryId;
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

  public void setGranted(Timestamp ts) {
    granted = ts;
  }

  public String getLabel() {
    return getLabelId().get();
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getVerifier() {
    return verifier;
  }

  public void setVerifier(String reporter) {
    this.verifier = reporter;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  @Override
  public String toString() {
    return new StringBuilder().append('[').append(key).append(": ")
        .append(value).append(']').toString();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof PatchSetVerification) {
      PatchSetVerification p = (PatchSetVerification) o;
      return Objects.equals(key, p.key)
          && Objects.equals(value, p.value)
          && Objects.equals(granted, p.granted);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, value, granted);
  }
}
