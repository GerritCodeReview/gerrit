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

package com.google.gerrit.extensions.common;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import com.google.gerrit.truth.ListSubject;

public class CommitInfoSubject extends Subject<CommitInfoSubject, CommitInfo> {

  private static final SubjectFactory<CommitInfoSubject, CommitInfo> COMMIT_INFO_SUBJECT_FACTORY =
      new SubjectFactory<CommitInfoSubject, CommitInfo>() {
        @Override
        public CommitInfoSubject getSubject(
            FailureStrategy failureStrategy, CommitInfo commitInfo) {
          return new CommitInfoSubject(failureStrategy, commitInfo);
        }
      };

  public static CommitInfoSubject assertThat(CommitInfo commitInfo) {
    return assertAbout(COMMIT_INFO_SUBJECT_FACTORY).that(commitInfo);
  }

  private CommitInfoSubject(FailureStrategy failureStrategy, CommitInfo commitInfo) {
    super(failureStrategy, commitInfo);
  }

  public StringSubject commit() {
    isNotNull();
    CommitInfo commitInfo = actual();
    return Truth.assertThat(commitInfo.commit).named("commit");
  }

  public ListSubject<CommitInfoSubject, CommitInfo> parents() {
    isNotNull();
    CommitInfo commitInfo = actual();
    return ListSubject.assertThat(commitInfo.parents, CommitInfoSubject::assertThat)
        .named("parents");
  }

  public GitPersonSubject committer() {
    isNotNull();
    CommitInfo commitInfo = actual();
    return GitPersonSubject.assertThat(commitInfo.committer).named("committer");
  }
}
