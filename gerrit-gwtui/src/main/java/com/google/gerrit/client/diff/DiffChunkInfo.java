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

/** Object recording the position of a diff chunk and whether it's an edit */
class DiffChunkInfo implements Comparable<DiffChunkInfo> {
  final DisplaySide side;
  final int start;
  final int end;
  final boolean edit;

  private final int startOnOther;

  DiffChunkInfo(DisplaySide side, int start, int startOnOther, int end,
      boolean edit) {
    this.side = side;
    this.start = start;
    this.startOnOther = startOnOther;
    this.end = end;
    this.edit = edit;
  }

  /**
   * Chunks are ordered by their starting line. If it's a deletion, use its
   * corresponding line on the revision side for comparison. In the edit case,
   * put the deletion chunk right before the insertion chunk. This placement
   * guarantees well-ordering.
   */
  @Override
  public int compareTo(DiffChunkInfo o) {
    if (side == o.side) {
      return start - o.start;
    } else if (side == DisplaySide.A) {
      int comp = startOnOther - o.start;
      return comp == 0 ? -1 : comp;
    } else {
      int comp = start - o.startOnOther;
      return comp == 0 ? 1 : comp;
    }
  }
}