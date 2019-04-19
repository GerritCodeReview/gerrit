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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.primitives.Ints;
import java.util.List;

/** A single modified file in a {@link PatchSet}. */
public final class Patch {
  /** Magical file name which represents the commit message. */
  public static final String COMMIT_MSG = "/COMMIT_MSG";

  /** Magical file name which represents the merge list of a merge commit. */
  public static final String MERGE_LIST = "/MERGE_LIST";

  /**
   * Checks if the given path represents a magic file. A magic file is a generated file that is
   * automatically included into changes. It does not exist in the commit of the patch set.
   *
   * @param path the file path
   * @return {@code true} if the path represents a magic file, otherwise {@code false}.
   */
  public static boolean isMagic(String path) {
    return COMMIT_MSG.equals(path) || MERGE_LIST.equals(path);
  }

  public static Key key(PatchSet.Id patchSetId, String fileName) {
    return new AutoValue_Patch_Key(patchSetId, fileName);
  }

  @AutoValue
  public abstract static class Key {
    /** Parse a Patch.Key out of a string representation. */
    public static Key parse(String str) {
      List<String> parts = Splitter.on(',').limit(3).splitToList(str);
      checkKeyFormat(parts.size() == 3, str);
      Integer changeId = Ints.tryParse(parts.get(0));
      checkKeyFormat(changeId != null, str);
      Integer patchSetNum = Ints.tryParse(parts.get(1));
      checkKeyFormat(patchSetNum != null, str);
      return key(new PatchSet.Id(new Change.Id(changeId), patchSetNum), parts.get(2));
    }

    private static void checkKeyFormat(boolean test, String input) {
      checkArgument(test, "invalid patch key: %s", input);
    }

    public abstract PatchSet.Id patchSetId();

    public abstract String fileName();

    public PatchSet.Id getParentKey() {
      return patchSetId();
    }
  }

  /** Type of modification made to the file path. */
  public enum ChangeType implements CodedEnum {
    /** Path is being created/introduced by this patch. */
    ADDED('A'),

    /** Path already exists, and has updated content. */
    MODIFIED('M'),

    /** Path existed, but is being removed by this patch. */
    DELETED('D'),

    /** Path existed at {@link Patch#getSourceFileName()} but was moved. */
    RENAMED('R'),

    /** Path was copied from {@link Patch#getSourceFileName()}. */
    COPIED('C'),

    /** Sufficient amount of content changed to claim the file was rewritten. */
    REWRITE('W');

    private final char code;

    ChangeType(char c) {
      code = c;
    }

    @Override
    public char getCode() {
      return code;
    }

    public boolean matches(String s) {
      return s != null && s.length() == 1 && s.charAt(0) == code;
    }

    public static ChangeType forCode(char c) {
      for (ChangeType s : ChangeType.values()) {
        if (s.code == c) {
          return s;
        }
      }
      return null;
    }
  }

  /** Type of formatting for this patch. */
  public enum PatchType implements CodedEnum {
    /**
     * A textual difference between two versions.
     *
     * <p>A UNIFIED patch can be rendered in multiple ways. Most commonly, it is rendered as a side
     * by side display using two columns, left column for the old version, right column for the new
     * version. A UNIFIED patch can also be formatted in a number of standard "patch script" styles,
     * but typically is formatted in the POSIX standard unified diff format.
     *
     * <p>Usually Gerrit renders a UNIFIED patch in a PatchScreen.SideBySide view, presenting the
     * file in two columns. If the user chooses, a PatchScreen.Unified is also a valid display
     * method.
     */
    UNIFIED('U'),

    /**
     * Difference of two (or more) binary contents.
     *
     * <p>A BINARY patch cannot be viewed in a text display, as it represents a change in binary
     * content at the associated path, for example, an image file has been replaced with a different
     * image.
     *
     * <p>Gerrit can only render a BINARY file in a PatchScreen.Unified view, as the only
     * information it can display is the old and new file content hashes.
     */
    BINARY('B');

    private final char code;

    PatchType(char c) {
      code = c;
    }

    @Override
    public char getCode() {
      return code;
    }

    public static PatchType forCode(char c) {
      for (PatchType s : PatchType.values()) {
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

  /** Number of published comments on this patch. */
  protected int nbrComments;

  /** Number of drafts by the current user; not persisted in the datastore. */
  protected int nbrDrafts;

  /** Number of lines added to the file. */
  protected int insertions;

  /** Number of lines deleted from the file. */
  protected int deletions;

  /** Original if {@link #changeType} is {@link ChangeType#COPIED} or {@link ChangeType#RENAMED}. */
  protected String sourceFileName;

  /** True if this patch has been reviewed by the current logged in user */
  private boolean reviewedByCurrentUser;

  protected Patch() {}

  public Patch(Patch.Key newId) {
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

  public void setCommentCount(int n) {
    nbrComments = n;
  }

  public int getDraftCount() {
    return nbrDrafts;
  }

  public void setDraftCount(int n) {
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

  public void setChangeType(ChangeType type) {
    changeType = type.getCode();
  }

  public PatchType getPatchType() {
    return PatchType.forCode(patchType);
  }

  public void setPatchType(PatchType type) {
    patchType = type.getCode();
  }

  public String getFileName() {
    return key.fileName();
  }

  public String getSourceFileName() {
    return sourceFileName;
  }

  public void setSourceFileName(String n) {
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
