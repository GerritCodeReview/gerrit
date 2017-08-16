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

import com.google.common.truth.ComparableSubject;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import java.sql.Timestamp;

public class GitPersonSubject extends Subject<GitPersonSubject, GitPerson> {

  private static final SubjectFactory<GitPersonSubject, GitPerson> GIT_PERSON_SUBJECT_FACTORY =
      new SubjectFactory<GitPersonSubject, GitPerson>() {
        @Override
        public GitPersonSubject getSubject(FailureStrategy failureStrategy, GitPerson gitPerson) {
          return new GitPersonSubject(failureStrategy, gitPerson);
        }
      };

  public static GitPersonSubject assertThat(GitPerson gitPerson) {
    return assertAbout(GIT_PERSON_SUBJECT_FACTORY).that(gitPerson);
  }

  private GitPersonSubject(FailureStrategy failureStrategy, GitPerson gitPerson) {
    super(failureStrategy, gitPerson);
  }

  public ComparableSubject<?, Timestamp> creationDate() {
    isNotNull();
    GitPerson gitPerson = actual();
    return Truth.assertThat(gitPerson.date).named("creationDate");
  }
}
