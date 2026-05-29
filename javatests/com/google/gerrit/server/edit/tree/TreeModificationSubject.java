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

package com.google.gerrit.server.edit.tree;

import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.gerrit.truth.ListSubject;
import java.util.List;

public class TreeModificationSubject extends Subject {

  public static TreeModificationSubject assertThat(TreeModification treeModification) {
    return assertAbout(treeModifications()).that(treeModification);
  }

  private static Factory<TreeModificationSubject, TreeModification> treeModifications() {
    return TreeModificationSubject::new;
  }

  public static ListSubject<TreeModificationSubject, TreeModification> assertThatList(
      List<TreeModification> treeModifications) {
    return ListSubject.assertThat(treeModifications, treeModifications());
  }

  private final TreeModification treeModification;

  private TreeModificationSubject(
      FailureMetadata failureMetadata, TreeModification treeModification) {
    super(failureMetadata, treeModification);
    this.treeModification = treeModification;
  }

  public ChangeFileContentModificationSubject asChangeFileContentModification() {
    isInstanceOf(ChangeFileContentModification.class);
    return check("asChangeFileContentModification()")
        .about(ChangeFileContentModificationSubject.modifications())
        .that((ChangeFileContentModification) treeModification);
  }
}
