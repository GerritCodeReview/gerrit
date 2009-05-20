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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.StringKey;

/** A single modified file in a {@link PatchSet}. */
public final class Patch {
  public static class Key extends StringKey<PatchSet.Id> {
    private static final long serialVersionUID = 1L;

    @Column(name = Column.NONE)
    protected PatchSet.Id patchSetId;

    @Column
    protected String fileName;

    protected Key() {
      patchSetId = new PatchSet.Id();
    }

    public Key(final PatchSet.Id ps, final String name) {
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

    @Override
    protected void set(String newValue) {
      fileName = newValue;
    }

    /** Parse a Patch.Id out of a string representation. */
    public static Key parse(final String str) {
      final Key r = new Key();
      r.fromString(str);
      return r;
    }
  }

  public static enum ChangeType {
    ADDED('A'),

    MODIFIED('M'),

    DELETED('D'),

    RENAMED('R'),

    COPIED('C');

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

  public static enum PatchType {
    UNIFIED('U'),

    BINARY('B'),

    N_WAY('N');

    private final char code;

    private PatchType(final char c) {
      code = c;
    }

    public char getCode() {
      return code;
    }

    public static PatchType forCode(final char c) {
      for (final PatchType s : PatchType.values()) {
        if (s.code == c) {
          return s;
        }
      }
      return null;
    }
  }

  @Column(name = Column.NONE)
  protected Key key;

  /** What sort of change is this to the path; see {@link ChangeType}. */
  @Column
  protected char changeType;

  /** What type of patch is this; see {@link PatchType}. */
  @Column
  protected char patchType;

  /** Number of published comments on this patch. */
  @Column
  protected int nbrComments;

  /** Number of drafts by the current user; not persisted in the datastore. */
  protected int nbrDrafts;

  /**
   * Original if {@link #changeType} is {@link ChangeType#COPIED} or
   * {@link ChangeType#RENAMED}.
   */
  @Column(notNull = false)
  protected String sourceFileName;

  protected Patch() {
  }

  public Patch(final Patch.Key newId) {
    key = newId;
    setChangeType(ChangeType.MODIFIED);
    setPatchType(PatchType.UNIFIED);
  }

  public Patch.Key getKey() {
    return key;
  }

  public int getCommentCount() {
    return nbrComments;
  }

  public void setCommentCount(final int n) {
    nbrComments = n;
  }

  public int getDraftCount() {
    return nbrDrafts;
  }

  public void setDraftCount(final int n) {
    nbrDrafts = n;
  }

  public ChangeType getChangeType() {
    return ChangeType.forCode(changeType);
  }

  public void setChangeType(final ChangeType type) {
    changeType = type.getCode();
  }

  public PatchType getPatchType() {
    return PatchType.forCode(patchType);
  }

  public void setPatchType(final PatchType type) {
    patchType = type.getCode();
  }

  public String getFileName() {
    return key.fileName;
  }

  public String getSourceFileName() {
    return sourceFileName;
  }

  public void setSourceFileName(final String n) {
    sourceFileName = n;
  }
  
  public String toString() {
    return key.toString();
  }
}
