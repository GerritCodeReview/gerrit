// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class MergeListBuilder {
  public static List<RevCommit> build(RevWalk rw, RevCommit merge, int uninterestingParent)
      throws IOException {
    rw.reset();
    rw.parseBody(merge);
    if (merge.getParentCount() < 2) {
      return ImmutableList.of();
    }

    for (int parent = 0; parent < merge.getParentCount(); parent++) {
      RevCommit parentCommit = merge.getParent(parent);
      rw.parseBody(parentCommit);
      if (parent == uninterestingParent - 1) {
        rw.markUninteresting(parentCommit);
      } else {
        rw.markStart(parentCommit);
      }
    }

    List<RevCommit> result = new ArrayList<>();
    RevCommit c;
    while ((c = rw.next()) != null) {
      result.add(c);
    }
    return result;
  }
}
