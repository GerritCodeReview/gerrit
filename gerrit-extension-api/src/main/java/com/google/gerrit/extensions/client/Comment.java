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

package com.google.gerrit.extensions.client;

import java.sql.Timestamp;
import java.util.Objects;

public abstract class Comment {
  /**
   * Patch set number containing this commit.
   *
   * <p>Only set in contexts where comments may come from multiple patch sets.
   */
  public Integer patchSet;

  public String id;
  public String path;
  public Side side;
  public Integer parent;
  public Integer line; // value 0 or null indicates a file comment, normal lines start at 1
  public Range range;
  public String inReplyTo;
  public Timestamp updated;
  public String message;
  public Boolean unresolved;

  public static class Range {
    public int startLine; // 1-based, inclusive
    public int startCharacter; // 0-based, inclusive
    public int endLine; // 1-based, exclusive
    public int endCharacter; // 0-based, exclusive

    public boolean isValid() {
      return startLine > 0
          && startCharacter >= 0
          && endLine > 0
          && endCharacter >= 0
          && startLine <= endLine
          && (startLine != endLine || startCharacter <= endCharacter);
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof Range) {
        Range r = (Range) o;
        return Objects.equals(startLine, r.startLine)
            && Objects.equals(startCharacter, r.startCharacter)
            && Objects.equals(endLine, r.endLine)
            && Objects.equals(endCharacter, r.endCharacter);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(startLine, startCharacter, endLine, endCharacter);
    }

    @Override
    public String toString() {
      return "Range{"
          + "startLine="
          + startLine
          + ", startCharacter="
          + startCharacter
          + ", endLine="
          + endLine
          + ", endCharacter="
          + endCharacter
          + '}';
    }
  }

  public short side() {
    if (side == Side.PARENT) {
      return (short) (parent == null ? 0 : -parent.shortValue());
    }
    return 1;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o != null && getClass() == o.getClass()) {
      Comment c = (Comment) o;
      return Objects.equals(patchSet, c.patchSet)
          && Objects.equals(id, c.id)
          && Objects.equals(path, c.path)
          && Objects.equals(side, c.side)
          && Objects.equals(parent, c.parent)
          && Objects.equals(line, c.line)
          && Objects.equals(range, c.range)
          && Objects.equals(inReplyTo, c.inReplyTo)
          && Objects.equals(updated, c.updated)
          && Objects.equals(message, c.message)
          && Objects.equals(unresolved, c.unresolved);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(patchSet, id, path, side, parent, line, range, inReplyTo, updated, message);
  }
}
