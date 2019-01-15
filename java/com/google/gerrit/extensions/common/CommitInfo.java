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

package com.google.gerrit.extensions.common;

import static java.util.stream.Collectors.joining;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import java.util.List;
import java.util.Objects;

public class CommitInfo {
  public String commit;
  public List<CommitInfo> parents;
  public GitPerson author;
  public GitPerson committer;
  public String subject;
  public String message;
  public List<WebLinkInfo> webLinks;

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof CommitInfo)) {
      return false;
    }
    CommitInfo c = (CommitInfo) o;
    return Objects.equals(commit, c.commit)
        && Objects.equals(parents, c.parents)
        && Objects.equals(author, c.author)
        && Objects.equals(committer, c.committer)
        && Objects.equals(subject, c.subject)
        && Objects.equals(message, c.message)
        && Objects.equals(webLinks, c.webLinks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(commit, parents, author, committer, subject, message, webLinks);
  }

  @Override
  public String toString() {
    ToStringHelper helper = MoreObjects.toStringHelper(this).addValue(commit);
    if (parents != null) {
      helper.add("parents", parents.stream().map(p -> p.commit).collect(joining(", ")));
    }
    helper
        .add("author", author)
        .add("committer", committer)
        .add("subject", subject)
        .add("message", message);
    if (webLinks != null) {
      helper.add("webLinks", webLinks);
    }
    return helper.toString();
  }
}
