// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.fixes.testing;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.gerrit.truth.ListSubject.elements;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.gerrit.jgit.diff.ReplaceEdit;
import com.google.gerrit.truth.ListSubject;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.Edit.Type;

public class GitEditSubject extends Subject {
  public static GitEditSubject assertThat(Edit edit) {
    return assertAbout(gitEdits()).that(edit);
  }

  public static Subject.Factory<GitEditSubject, Edit> gitEdits() {
    return GitEditSubject::new;
  }

  private final Edit edit;

  private GitEditSubject(FailureMetadata failureMetadata, Edit edit) {
    super(failureMetadata, edit);
    this.edit = edit;
  }

  public GitEditSubject hasRegions(int beginA, int endA, int beginB, int endB) {
    isNotNull();
    check("beginA").that(edit.getBeginA()).isEqualTo(beginA);
    check("endA").that(edit.getEndA()).isEqualTo(endA);
    check("beginB").that(edit.getBeginB()).isEqualTo(beginB);
    check("endB").that(edit.getEndB()).isEqualTo(endB);
    return this;
  }

  public GitEditSubject hasType(Type type) {
    isNotNull();
    check("getType").that(edit.getType()).isEqualTo(type);
    return this;
  }

  public GitEditSubject isInsert(int insertPos, int beginB, int insertedLength) {
    isNotNull();
    return hasType(Type.INSERT)
        .hasRegions(insertPos, insertPos, beginB, beginB + insertedLength);

  }

  public GitEditSubject isDelete(int deletePos, int deletedLength, int posB) {
    isNotNull();
    return hasType(Type.DELETE)
      .hasRegions(deletePos, deletePos + deletedLength, posB, posB);
  }

  public GitEditSubject isReplace(int originalPos, int originalLength, int newPos, int newLength) {
    isNotNull();
    return hasType(Type.REPLACE)
      .hasRegions(originalPos, originalPos + originalLength, newPos, newPos + newLength);
  }

  public GitEditSubject isEmpty() {
    isNotNull();
    return hasType(Type.EMPTY);
  }

  public ListSubject<GitEditSubject, Edit> hasInternalEdits() {
    isNotNull();
    isInstanceOf(ReplaceEdit.class);
    return check("internalEdits").about(elements()).thatCustom(((ReplaceEdit)edit).getInternalEdits(), gitEdits());
  }
}
