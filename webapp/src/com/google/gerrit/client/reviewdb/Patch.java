// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.StringKey;

/** A single modified file in a {@link PatchSet}. */
public final class Patch {
  public static class Id extends StringKey<PatchSet.Id> {
    @Column(name = Column.NONE)
    protected PatchSet.Id patchSetId;

    @Column
    protected String fileName;

    protected Id() {
      patchSetId = new PatchSet.Id();
    }

    public Id(final PatchSet.Id ps, final String name) {
      this.patchSetId = ps;
      this.fileName = name;
    }

    @Override
    public PatchSet.Id getParentKey() {
      return patchSetId;
    }

    @Override
    public String get() {
      return fileName;
    }
  }

  public static enum ChangeType {
    ADD('A'),

    MODIFIED('M'),

    DELETED('D');

    private final char code;

    private ChangeType(final char c) {
      code = c;
    }

    public char getCode() {
      return code;
    }

    public static ChangeType forCode(final char c) {
      for (final ChangeType s : ChangeType.values()) {
        if (s.code == c) {
          return s;
        }
      }
      return null;
    }
  }

  @Column(name = Column.NONE)
  protected Id key;

  /** What sort of change is this to the path; see {@link ChangeType}. */
  @Column
  protected char changeType;

  /** Number of published comments on this patch. */
  @Column
  protected int nbrComments;

  protected Patch() {
  }

  public Patch(final Patch.Id newId, final ChangeType type) {
    key = newId;
    setChangeType(type);
  }

  public Patch.Id getKey() {
    return key;
  }

  public int getCommentCount() {
    return nbrComments;
  }

  public ChangeType getChangeType() {
    return ChangeType.forCode(changeType);
  }

  public void setChangeType(final ChangeType type) {
    changeType = type.getCode();
  }
}
