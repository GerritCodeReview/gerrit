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

public class SparseFileContentBuilder {
  private ImmutableList.Builder<Range> ranges;
  private int size;
  private int lastRangeBase;
  private int lastRangeEnd;
  private ImmutableList.Builder<String> lastRangeLines;

  public SparseFileContentBuilder() {
    ranges = new ImmutableList.Builder();
    startNextRange(0);
  }

  public void setSize(int s) {
    size = s;
  }

  public void addLine(int lineNumber, String content) {
    final Range r;
    if (lineNumber < lastRangeEnd) {
      throw new IllegalArgumentException("Invalid line number: " + lineNumber);
    }
    if (lineNumber > lastRangeEnd) {
      finishLastRange();
      startNextRange(lineNumber);
    }
    lastRangeLines.add(content);
    lastRangeEnd++;
  }

  private void startNextRange(int base) {
    lastRangeLines = new ImmutableList.Builder();
    lastRangeBase = lastRangeEnd = base;
  }

  private void finishLastRange() {
    if (lastRangeEnd > lastRangeBase) {
      ranges.add(Range.create(lastRangeBase, lastRangeLines.build()));
      lastRangeLines = null;
    }
  }

  public SparseFileContent build() {
    if (lastRangeEnd > size) {
      throw new IllegalStateException("size value is not valid");
    }
    finishLastRange();
    return SparseFileContent.create(ranges.build(), size);
  }
}
