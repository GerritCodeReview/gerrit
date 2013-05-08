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

package com.google.gerrit.server.git.requests;

import com.google.gerrit.server.git.CodeReviewCommit;

import org.eclipse.jgit.revwalk.FooterKey;

import java.util.List;

public abstract class GitRequest {
  public static String getRequestFooterValue(final CodeReviewCommit commit, final FooterKey key)
      throws GitRequestException {
    List<String> footers = commit.getFooterLines(key);
    if (footers.isEmpty()) {
      throw new GitRequestException("Invalid request commit: missing " +
          key.toString() + " footer");
    }
    if (footers.size() > 1) {
      throw new GitRequestException("Invalid request commit: too many " +
          key.toString() + " footers");
    }
    return footers.get(0);
  }
};
