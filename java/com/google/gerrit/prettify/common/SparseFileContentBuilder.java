// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.prettify.common;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.prettify.common.SparseFileContent.Range;

/**
 * A builder for creating immutable {@link SparseFileContent}. Lines can be only be added in
 * sequential (increased) order
 */
public class SparseFileContentBuilder {
  private final ImmutableList.Builder<Range> ranges;
  private final int size;
  private int lastRangeBase;
  private int lastRangeEnd;
  private ImmutableList.Builder<String> lastRangeLines;

  public SparseFileContentBuilder(int size) {
    ranges = new ImmutableList.Builder<>();
    startNextRange(0);
    this.size = size;
  }

  public void addLine(int lineNumber, String content) {
    if (lineNumber < 0) {
      throw new IllegalArgumentException("Line number must be non-negative");
    }
    //    if (lineNumber >= size) {
    //     The following 4 tests are failed if you uncomment this condition:
    //
    //
    // diffOfFileWithMultilineRebaseHunkRemovingNewlineAtEndOfFileAndWithCommentReturnsFileContents
    //
    // diffOfFileWithMultilineRebaseHunkAddingNewlineAtEndOfFileAndWithCommentReturnsFileContents
    //
    //
    // diffOfFileWithMultilineRebaseHunkRemovingNewlineAtEndOfFileAndWithCommentReturnsFileContents
    //
    // diffOfFileWithMultilineRebaseHunkAddingNewlineAtEndOfFileAndWithCommentReturnsFileContents
    //     Tests are failed because there are some bug with diff calculation.
    //     The condition must be uncommented after all these bugs are fixed.
    //     Also don't forget to remove ignore from for SparseFileContentBuilder
    //      throw new IllegalArgumentException(String.format("The zero-based line number %d is after
    // the end of file. The file size is %d line(s).", lineNumber, size));
    //    }
    if (lineNumber < lastRangeEnd) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid line number %d. You are trying to add a line before an already added line"
                  + " %d",
              lineNumber, lastRangeEnd));
    }
    if (lineNumber > lastRangeEnd) {
      finishLastRange();
      startNextRange(lineNumber);
    }
    lastRangeLines.add(content);
    lastRangeEnd++;
  }

  private void startNextRange(int base) {
    lastRangeLines = new ImmutableList.Builder<>();
    lastRangeBase = lastRangeEnd = base;
  }

  private void finishLastRange() {
    if (lastRangeEnd > lastRangeBase) {
      ranges.add(Range.create(lastRangeBase, lastRangeLines.build()));
      lastRangeLines = null;
    }
  }

  public SparseFileContent build() {
    finishLastRange();
    return SparseFileContent.create(ranges.build(), size);
  }
}
