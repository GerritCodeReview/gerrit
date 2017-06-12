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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CommonConverters;
import com.google.inject.Singleton;
import java.util.ArrayList;
import org.eclipse.jgit.revwalk.RevCommit;

@Singleton
public class GetCommit implements RestReadView<CommitResource> {

  @Override
  public CommitInfo apply(CommitResource rsrc) {
    return toCommitInfo(rsrc.getCommit());
  }

  private static CommitInfo toCommitInfo(RevCommit commit) {
    CommitInfo info = new CommitInfo();
    info.commit = commit.getName();
    info.author = CommonConverters.toGitPerson(commit.getAuthorIdent());
    info.committer = CommonConverters.toGitPerson(commit.getCommitterIdent());
    info.subject = commit.getShortMessage();
    info.message = commit.getFullMessage();
    info.parents = new ArrayList<>(commit.getParentCount());
    for (int i = 0; i < commit.getParentCount(); i++) {
      RevCommit p = commit.getParent(i);
      CommitInfo parentInfo = new CommitInfo();
      parentInfo.commit = p.getName();
      parentInfo.subject = p.getShortMessage();
      info.parents.add(parentInfo);
    }
    return info;
  }
}
