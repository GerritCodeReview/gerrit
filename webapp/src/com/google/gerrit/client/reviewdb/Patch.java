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

  /** Content which expands out to the old version of the file. */
  @Column(notNull = false)
  protected DeltaContent.Key preImage;

  /** Unified style diff between {@link #preImage} and {@link #postImage}. */
  @Column
  protected DeltaContent.Key patch;

  /**
   * Content which expands out to the new version of the file.
   * <p>
   * Note that in many cases this is identical to {@link #patch}, as very often
   * the patch itself can be used to reconstruct the postImage.
   */
  @Column(notNull = false)
  protected DeltaContent.Key postImage;

  protected Patch() {
  }

  public Patch(final Patch.Id newId, final ChangeType type) {
    key = newId;
    setChangeType(type);
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

  public DeltaContent.Key getPreImage() {
    return preImage;
  }

  public void setPreImage(final DeltaContent.Key p) {
    preImage = p;
  }

  public DeltaContent.Key getPatch() {
    return patch;
  }

  public void setPatch(final DeltaContent.Key p) {
    patch = p;
  }

  public DeltaContent.Key getPostImage() {
    return postImage;
  }

  public void setPostImage(final DeltaContent.Key p) {
    postImage = p;
  }
}
