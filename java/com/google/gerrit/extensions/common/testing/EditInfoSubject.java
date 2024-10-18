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
import static com.google.gerrit.extensions.common.testing.CommitInfoSubject.commits;
import static com.google.gerrit.truth.MapSubject.mapEntries;

import com.google.common.truth.BooleanSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.truth.MapSubject;
import com.google.gerrit.truth.OptionalSubject;
import java.util.Optional;

public class EditInfoSubject extends Subject {

  public static EditInfoSubject assertThat(EditInfo editInfo) {
    return assertAbout(edits()).that(editInfo);
  }

  private static Subject.Factory<EditInfoSubject, EditInfo> edits() {
    return EditInfoSubject::new;
  }

  public static OptionalSubject<EditInfoSubject, EditInfo> assertThat(
      Optional<EditInfo> editInfoOptional) {
    return OptionalSubject.assertThat(editInfoOptional, edits());
  }

  private final EditInfo editInfo;

  private EditInfoSubject(FailureMetadata failureMetadata, EditInfo editInfo) {
    super(failureMetadata, editInfo);
    this.editInfo = editInfo;
  }

  public CommitInfoSubject commit() {
    isNotNull();
    return check("commit").about(commits()).that(editInfo.commit);
  }

  public StringSubject baseRevision() {
    isNotNull();
    return check("baseRevision").that(editInfo.baseRevision);
  }

  public MapSubject files() {
    isNotNull();
    return check("files").about(mapEntries()).that(editInfo.files);
  }

  public BooleanSubject containsGitConflicts() {
    isNotNull();
    return check("containsGitConflicts")
        .that(editInfo.containsGitConflicts != null ? editInfo.containsGitConflicts : false);
  }
}
