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
  public Integer line;
  public Range range;
  public String inReplyTo;
  public Timestamp updated;
  public String message;

  public static class Range {
    public int startLine;
    public int startCharacter;
    public int endLine;
    public int endCharacter;

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
          && Objects.equals(message, c.message);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(patchSet, id, path, side, parent, line, range, inReplyTo, updated, message);
  }
}
