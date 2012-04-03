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
import com.google.gwtorm.client.StringKey;

import java.util.Map;

/** A single modified file in a {@link PatchSet}. */
public final class Patch {
  /** Magical file name which represents the commit message. */
  public static final String COMMIT_MSG = "/COMMIT_MSG";

  public static class Key extends StringKey<PatchSet.Id> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1, name = Column.NONE)
    protected PatchSet.Id patchSetId;

    @Column(id = 2)
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

    public String getFileName() {
      return get();
    }
  }

  /** Type of modification made to the file path. */
  public static enum ChangeType implements CodedEnum {
    /** Path is being created/introduced by this patch. */
    ADDED('A'),

    /** Path already exists, and has updated content. */
    MODIFIED('M'),

    /** Path existed, but is being removed by this patch. */
    DELETED('D'),

    /** Path existed at {@link Patch#getSourceFileName()} but was moved. */
    RENAMED('R'),

    /** Path was copied from {@link Patch#getSourceFileName()}. */
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

  /** Type of formatting for this patch. */
  public static enum PatchType implements CodedEnum {
    /**
     * A textual difference between two versions.
     *
     * <p>
     * A UNIFIED patch can be rendered in multiple ways. Most commonly, it is
     * rendered as a side by side display using two columns, left column for the
     * old version, right column for the new version. A UNIFIED patch can also
     * be formatted in a number of standard "patch script" styles, but typically
     * is formatted in the POSIX standard unified diff format.
     *
     * <p>
     * Usually Gerrit renders a UNIFIED patch in a PatchScreen.SideBySide view,
     * presenting the file in two columns. If the user chooses, a
     * PatchScreen.Unified is also a valid display method.
     * */
    UNIFIED('U'),

    /**
     * Difference of two (or more) binary contents.
     *
     * <p>
     * A BINARY patch cannot be viewed in a text display, as it represents a
     * change in binary content at the associated path, for example, an image
     * file has been replaced with a different image.
     *
     * <p>
     * Gerrit can only render a BINARY file in a PatchScreen.Unified view, as
     * the only information it can display is the old and new file content
     * hashes.
     */
    BINARY('B');

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

  protected Key key;

  /** What sort of change is this to the path; see {@link ChangeType}. */
  protected char changeType;

  /** What type of patch is this; see {@link PatchType}. */
  protected char patchType;

  /**
   * Number of published comments per account.
   *
   * TODO: Using String for the value as I couldn't get proper (de)serialization
   * of Integer.
   */
  protected Map<Account.Id, String> commentCounts;

  /** Number of drafts by the current user; not persisted in the datastore. */
  protected int nbrDrafts;

  /** Number of lines added to the file. */
  protected int insertions;

  /** Number of lines deleted from the file. */
  protected int deletions;

  /**
   * Original if {@link #changeType} is {@link ChangeType#COPIED} or
   * {@link ChangeType#RENAMED}.
   */
  protected String sourceFileName;

  /** True if this patch has been reviewed by the current logged in user */
  private boolean reviewedByCurrentUser;

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
    if (commentCounts == null) {
      return 0;
    }
    int n = 0;
    for (String c : commentCounts.values()) {
      n += Integer.parseInt(c);
    }
    return n;
  }

  public void setCommentCounts(Map<Account.Id, String> counts) {
    commentCounts = counts;
  }

  public Map<Account.Id, String> getCommentCounts() {
    return commentCounts;
  }

  public int getDraftCount() {
    return nbrDrafts;
  }

  public void setDraftCount(final int n) {
    nbrDrafts = n;
  }

  public int getInsertions() {
    return insertions;
  }

  public void setInsertions(int n) {
    insertions = n;
  }

  public int getDeletions() {
    return deletions;
  }

  public void setDeletions(int n) {
    deletions = n;
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

  public boolean isReviewedByCurrentUser() {
    return reviewedByCurrentUser;
  }

  public void setReviewedByCurrentUser(boolean r) {
    reviewedByCurrentUser = r;
  }

  @Override
  public String toString() {
    return "[Patch " + getKey().toString() + "]";
  }
}
