// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.testing;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.git.CommitUtil;
import java.io.IOException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;

public class GitTestUtil {
  public static ImmutableList<CommitInfo> log(Repository repo, String refName) throws Exception {
    try (RevWalk rw = new RevWalk(repo)) {
      Ref ref = repo.exactRef(refName);
      if (ref != null) {
        rw.sort(RevSort.REVERSE);
        rw.markStart(rw.parseCommit(ref.getObjectId()));
        return Streams.stream(rw)
            .map(
                c -> {
                  try {
                    return CommitUtil.toCommitInfo(c);
                  } catch (IOException e) {
                    throw new IllegalStateException(
                        "unexpected state when converting commit " + c.getName(), e);
                  }
                })
            .collect(toImmutableList());
      }
    }
    return ImmutableList.of();
  }
}
