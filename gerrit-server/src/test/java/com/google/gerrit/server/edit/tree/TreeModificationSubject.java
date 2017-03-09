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

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;

public class TreeModificationSubject extends Subject<TreeModificationSubject, TreeModification> {

  private static final SubjectFactory<TreeModificationSubject, TreeModification>
      TREE_MODIFICATION_SUBJECT_FACTORY =
          new SubjectFactory<TreeModificationSubject, TreeModification>() {
            @Override
            public TreeModificationSubject getSubject(
                FailureStrategy failureStrategy, TreeModification treeModification) {
              return new TreeModificationSubject(failureStrategy, treeModification);
            }
          };

  public static TreeModificationSubject assertThat(TreeModification treeModification) {
    return assertAbout(TREE_MODIFICATION_SUBJECT_FACTORY).that(treeModification);
  }

  private TreeModificationSubject(
      FailureStrategy failureStrategy, TreeModification treeModification) {
    super(failureStrategy, treeModification);
  }

  public ChangeFileContentModificationSubject asChangeFileContentModification() {
    isInstanceOf(ChangeFileContentModification.class);
    return ChangeFileContentModificationSubject.assertThat(
        (ChangeFileContentModification) actual());
  }
}
