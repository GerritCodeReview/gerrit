// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.git;

import java.io.IOException;
import java.util.Iterator;

class FastForwardOnly extends MergeOp {
  FastForwardOnly(final MergeArguments margs) {
    super(margs);
  }

  @Override
  public void runMerge() throws MergeException {
    reduceToMinimalMerge();

    for (final Iterator<CodeReviewCommit> i = toMerge.iterator(); i.hasNext();) {
      try {
        final CodeReviewCommit n = i.next();
        if (mergeTip == null || rw.isMergedInto(mergeTip, n)) {
          mergeTip = n;
          i.remove();
          break;
        }
      } catch (IOException e) {
        throw new MergeException("Cannot fast-forward test during merge", e);
      }
    }

    // If this project only permits fast-forwards, abort everything else.
    //
    while (!toMerge.isEmpty()) {
      final CodeReviewCommit n = toMerge.remove(0);
      n.statusCode = CommitMergeStatus.NOT_FAST_FORWARD;
    }
    markCleanMerges();
  }
}