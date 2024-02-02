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

import com.google.gerrit.extensions.common.FixSuggestionInfo;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
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
  /** Value 0 or null indicates a file comment, normal lines start at 1. */
  public Integer line;

  public Range range;
  public String inReplyTo;

  // TODO(issue-15508): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  public Timestamp updated;

  public String message;

  /**
   * Hex commit SHA1 (as 40 characters hex string) of the commit of the patchset to which this
   * comment applies.
   */
  public String commitId;

  public List<FixSuggestionInfo> fixSuggestions;

  // TODO(issue-15508): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  @SuppressWarnings("JdkObsolete")
  public Instant getUpdated() {
    return updated.toInstant();
  }

  // TODO(issue-15508): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  @SuppressWarnings("JdkObsolete")
  public void setUpdated(Instant when) {
    updated = Timestamp.from(when);
  }

  public static class Range implements Comparable<Range> {
    private static final Comparator<Range> RANGE_COMPARATOR =
        Comparator.<Range>comparingInt(range -> range.startLine)
            .thenComparingInt(range -> range.startCharacter)
            .thenComparingInt(range -> range.endLine)
            .thenComparingInt(range -> range.endCharacter);

    // Start position is inclusive; end position is exclusive.
    public int startLine; // 1-based
    public int startCharacter; // 0-based
    public int endLine; // 1-based
    public int endCharacter; // 0-based

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
        return startLine == r.startLine
            && startCharacter == r.startCharacter
            && endLine == r.endLine
            && endCharacter == r.endCharacter;
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

    @Override
    public int compareTo(Range otherRange) {
      return RANGE_COMPARATOR.compare(this, otherRange);
    }
  }

  public short side() {
    if (side == Side.PARENT) {
      return (short) (parent == null ? 0 : -parent.shortValue());
    }
    return 1;
  }

  // This is a value class that allows adding attributes by subclassing.
  // Doing this is discouraged and using composition rather than inheritance to add fields to value
  // types is preferred. However this class is part of the extension API, hence we cannot change it
  // without breaking the API. Hence suppress the EqualsGetClass warning here.
  @SuppressWarnings("EqualsGetClass")
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
          && Objects.equals(commitId, c.commitId)
          && Objects.equals(fixSuggestions, c.fixSuggestions);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        patchSet, id, path, side, parent, line, range, inReplyTo, updated, message, fixSuggestions);
  }
}
