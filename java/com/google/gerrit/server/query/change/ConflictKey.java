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

package com.google.gerrit.server.query.change;

import com.google.gerrit.extensions.client.SubmitType;
import java.io.Serializable;
import java.util.Objects;
import org.eclipse.jgit.lib.ObjectId;

public class ConflictKey implements Serializable {
  private static final long serialVersionUID = 2L;

  private final ObjectId commit;
  private final ObjectId otherCommit;
  private final SubmitType submitType;
  private final boolean contentMerge;

  public ConflictKey(
      ObjectId commit, ObjectId otherCommit, SubmitType submitType, boolean contentMerge) {
    if (SubmitType.FAST_FORWARD_ONLY.equals(submitType) || commit.compareTo(otherCommit) < 0) {
      this.commit = commit;
      this.otherCommit = otherCommit;
    } else {
      this.commit = otherCommit;
      this.otherCommit = commit;
    }
    this.submitType = submitType;
    this.contentMerge = contentMerge;
  }

  public ObjectId getCommit() {
    return commit;
  }

  public ObjectId getOtherCommit() {
    return otherCommit;
  }

  public SubmitType getSubmitType() {
    return submitType;
  }

  public boolean isContentMerge() {
    return contentMerge;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ConflictKey)) {
      return false;
    }
    ConflictKey other = (ConflictKey) o;
    return commit.equals(other.commit)
        && otherCommit.equals(other.otherCommit)
        && submitType.equals(other.submitType)
        && contentMerge == other.contentMerge;
  }

  @Override
  public int hashCode() {
    return Objects.hash(commit, otherCommit, submitType, contentMerge);
  }
}
