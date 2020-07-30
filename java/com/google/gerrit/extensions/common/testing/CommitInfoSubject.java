// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.extensions.common.testing;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.gerrit.extensions.common.testing.GitPersonSubject.gitPersons;
import static com.google.gerrit.truth.ListSubject.elements;

import com.google.common.truth.Correspondence;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.truth.ListSubject;
import com.google.gerrit.truth.NullAwareCorrespondence;

public class CommitInfoSubject extends Subject {

  public static CommitInfoSubject assertThat(CommitInfo commitInfo) {
    return assertAbout(commits()).that(commitInfo);
  }

  public static Subject.Factory<CommitInfoSubject, CommitInfo> commits() {
    return CommitInfoSubject::new;
  }

  private final CommitInfo commitInfo;

  private CommitInfoSubject(FailureMetadata failureMetadata, CommitInfo commitInfo) {
    super(failureMetadata, commitInfo);
    this.commitInfo = commitInfo;
  }

  public StringSubject commit() {
    isNotNull();
    return check("commit").that(commitInfo.commit);
  }

  public ListSubject<CommitInfoSubject, CommitInfo> parents() {
    isNotNull();
    return check("parents").about(elements()).thatCustom(commitInfo.parents, commits());
  }

  public GitPersonSubject committer() {
    isNotNull();
    return check("committer").about(gitPersons()).that(commitInfo.committer);
  }

  public GitPersonSubject author() {
    isNotNull();
    return check("author").about(gitPersons()).that(commitInfo.author);
  }

  public StringSubject message() {
    isNotNull();
    return check("message").that(commitInfo.message);
  }

  public static Correspondence<CommitInfo, String> hasCommit() {
    return NullAwareCorrespondence.transforming(commitInfo -> commitInfo.commit, "hasCommit");
  }
}
