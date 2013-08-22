// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.diff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Helper class to handle calculations involving line gaps. */
class LineMapper {
  private int lineA;
  private int lineB;
  private List<LineGap> lineMapAtoB;
  private List<LineGap> lineMapBtoA;

  LineMapper() {
    lineMapAtoB = new ArrayList<LineGap>();
    lineMapBtoA = new ArrayList<LineGap>();
  }

  int getLineA() {
    return lineA;
  }

  int getLineB() {
    return lineB;
  }

  void appendCommon(int numLines) {
    lineA += numLines;
    lineB += numLines;
  }

  void appendInsert(int numLines) {
    int origLineB = lineB;
    lineB += numLines;
    int bAheadOfA = lineB - lineA;
    lineMapAtoB.add(new LineGap(lineA, -1, bAheadOfA));
    lineMapBtoA.add(new LineGap(origLineB, lineB - 1, -bAheadOfA));
  }

  void appendDelete(int numLines) {
    int origLineA = lineA;
    lineA += numLines;
    int aAheadOfB = lineA - lineB;
    lineMapAtoB.add(new LineGap(origLineA, lineA - 1, -aAheadOfB));
    lineMapBtoA.add(new LineGap(lineB, -1, aAheadOfB));
  }

  /**
   * Helper method to retrieve the line number on the other side.
   *
   * Given a line number on one side, performs a binary search in the lineMap
   * to find the corresponding LineGap record.
   *
   * A LineGap records gap information from the start of an actual gap up to
   * the start of the next gap. In the following example,
   * lineMapAtoB will have LineGap: {start: 1, end: -1, delta: 3}
   * (end set to -1 to represent a dummy gap of length zero. The binary search
   * only looks at start so setting it to -1 has no effect here.)
   * lineMapBtoA will have LineGap: {start: 1, end: 3, delta: -3}
   * These LineGaps control lines between 1 and 5.
   *
   * The "delta" is computed as the number to add on our side to get the line
   * number on the other side given a line after the actual gap, so the result
   * will be (line + delta). All lines within the actual gap (1 to 3) are
   * considered corresponding to the last line above the region on the other
   * side, which is 0 in this case. For these lines, we do (end + delta).
   *
   * For example, to get the line number on the left corresponding to 1 on the
   * right (lineOnOther(REVISION, 1)), the method looks up in lineMapBtoA,
   * finds the "delta" to be -3, and returns 3 + (-3) = 0 since 1 falls in the
   * actual gap. On the other hand, the line corresponding to 5 on the right
   * will be 5 + (-3) = 2, since 5 is in the region after the gap (but still
   * controlled by the current LineGap).
   *
   * PARENT REVISION
   *   0   |   0
   *   -   |   1 \                      \
   *   -   |   2 | Actual insertion gap |
   *   -   |   3 /                      | Region controlled by one LineGap
   *   1   |   4   <- delta = 4 - 1 = 3 |
   *   2   |   5                        /
   *   -   |   6
   *      ...
   */
  LineOnOtherInfo lineOnOther(DisplaySide mySide, int line) {
    List<LineGap> lineGaps = mySide == DisplaySide.A ? lineMapAtoB : lineMapBtoA;
    // Create a dummy LineGap for the search.
    int ret = Collections.binarySearch(lineGaps, new LineGap(line));
    if (ret == -1) {
      return new LineOnOtherInfo(line, true);
    } else {
      LineGap lookup = lineGaps.get(0 <= ret ? ret : -ret - 2);
      int start = lookup.start;
      int end = lookup.end;
      int delta = lookup.delta;
      if (start <= line && line <= end && end != -1) { // Line falls within gap
        return new LineOnOtherInfo(end + delta, false);
      } else { // Line after gap
        return new LineOnOtherInfo(line + delta, true);
      }
    }
  }

  /**
   * @field line The line number on the other side.
   * @field aligned Whether the two lines are at the same height when displayed.
   */
  static class LineOnOtherInfo {
    private int line;
    private boolean aligned;

    LineOnOtherInfo(int line, boolean aligned) {
      this.line = line;
      this.aligned = aligned;
    }

    int getLine() {
      return line;
    }

    boolean isAligned() {
      return aligned;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof LineOnOtherInfo) {
        LineOnOtherInfo other = (LineOnOtherInfo) obj;
        return aligned == other.aligned && line == other.line;
      }
      return false;
    }

    @Override
    public String toString() {
      return line + " " + aligned;
    }
  }

  /**
   * Helper class to record line gap info and assist in calculation of line
   * number on the other side.
   *
   * For a mapping from A to B, where A is the side with an insertion:
   * @field start The start line of the insertion in A.
   * @field end The exclusive end line of the insertion in A.
   * @field delta The offset added to A to get the line number in B calculated
   *              from end.
   */
  private static class LineGap implements Comparable<LineGap> {
    private final int start;
    private final int end;
    private final int delta;

    private LineGap(int start, int end, int delta) {
      this.start = start;
      this.end = end;
      this.delta = delta;
    }

    private LineGap(int line) {
      this(line, 0, 0);
    }

    @Override
    public int compareTo(LineGap o) {
      return start - o.start;
    }
  }
}
