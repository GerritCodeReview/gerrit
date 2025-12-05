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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import java.util.List;

/**
 * Wrapper class for patch related aspects. Originally, this class represented a single modified
 * file in a {@link PatchSet}. It's only kept in its current form as {@link ChangeType} and {@link
 * PatchType} are used in diff cache entries for which we would break the serialization if we moved
 * the enums somewhere else.
 */
public final class Patch {
  /** Magical file name which represents the commit message. */
  public static final String COMMIT_MSG = "/COMMIT_MSG";

  /** Magical file name which represents the merge list of a merge commit. */
  public static final String MERGE_LIST = "/MERGE_LIST";

  /**
   * Magical file name which doesn't represent a file. Used specifically for patchset-level
   * comments.
   */
  public static final String PATCHSET_LEVEL = "/PATCHSET_LEVEL";

  /**
   * Checks if the given path represents a magic file. A magic file is a generated file that is
   * automatically included into changes. It does not exist in the commit of the patch set.
   *
   * @param path the file path
   * @return {@code true} if the path represents a magic file, otherwise {@code false}.
   */
  public static boolean isMagic(String path) {
    return COMMIT_MSG.equals(path) || MERGE_LIST.equals(path) || PATCHSET_LEVEL.equals(path);
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
      return key(PatchSet.id(Change.id(changeId), patchSetNum), parts.get(2));
    }

    private static void checkKeyFormat(boolean test, String input) {
      checkArgument(test, "invalid patch key: %s", input);
    }

    public abstract PatchSet.Id patchSetId();

    public abstract String fileName();
  }

  /**
   * Type of modification made to the file path. Ordering of values matters (used by diff cache).
   */
  public enum ChangeType implements CodedEnum {
    /** Path is being created/introduced by this patch. */
    ADDED('A'),

    /** Path existed at the source but was moved. */
    RENAMED('R'),

    /** Path already exists, and has updated content. */
    MODIFIED('M'),

    /** Path was copied from the source. */
    COPIED('C'),

    /** Sufficient amount of content changed to claim the file was rewritten. */
    REWRITE('W'),

    /** Path existed, but is being removed by this patch. */
    DELETED('D');

    private final char code;

    ChangeType(char c) {
      code = c;
    }

    @Override
    public char getCode() {
      return code;
    }

    @UsedAt(UsedAt.Project.COLLABNET)
    @Nullable
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
  }

  /**
   * Constants describing various file modes recognized by GIT. This is the Gerrit entity for {@link
   * org.eclipse.jgit.lib.FileMode}.
   */
  public enum FileMode implements CodedEnum {
    /** Mode indicating an entry is a tree (aka directory). */
    TREE('T', 0040000),

    /** Mode indicating an entry is a symbolic link. */
    SYMLINK('S', 0120000),

    /** Mode indicating an entry is a non-executable file. */
    REGULAR_FILE('R', 0100644),

    /** Mode indicating an entry is an executable file. */
    EXECUTABLE_FILE('E', 0100755),

    /** Mode indicating an entry is a submodule commit in another repository. */
    GITLINK('G', 0160000),

    /** Mode indicating an entry is missing during parallel walks. */
    MISSING('M', 0000000);

    private final char code;

    private final int mode;

    FileMode(char c, int m) {
      code = c;
      mode = m;
    }

    @Override
    public char getCode() {
      return code;
    }

    public int getMode() {
      return mode;
    }

    public int getModeAsOctal() {
      return Integer.parseInt(Integer.toOctalString(getMode()));
    }
  }

  private Patch() {}
}
