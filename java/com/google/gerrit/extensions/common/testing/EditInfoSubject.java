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

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.truth.OptionalSubject;
import java.util.Optional;

public class EditInfoSubject extends Subject<EditInfoSubject, EditInfo> {

  public static EditInfoSubject assertThat(EditInfo editInfo) {
    return assertAbout(EditInfoSubject::new).that(editInfo);
  }

  public static OptionalSubject<EditInfoSubject, EditInfo> assertThat(
      Optional<EditInfo> editInfoOptional) {
    return OptionalSubject.assertThat(editInfoOptional, EditInfoSubject::assertThat);
  }

  private EditInfoSubject(FailureMetadata failureMetadata, EditInfo editInfo) {
    super(failureMetadata, editInfo);
  }

  public CommitInfoSubject commit() {
    isNotNull();
    EditInfo editInfo = actual();
    return CommitInfoSubject.assertThat(editInfo.commit).named("commit");
  }

  public StringSubject baseRevision() {
    isNotNull();
    EditInfo editInfo = actual();
    return Truth.assertThat(editInfo.baseRevision).named("baseRevision");
  }
}
