// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.prettify.common;

import com.google.gwtorm.client.Column;

import org.eclipse.jgit.diff.Edit;

import java.util.List;

public class LineEdit extends BaseEdit {
  @Column(id = 5)
  protected List<BaseEdit> edits;

  public LineEdit(int beginA, int endA, int beginB, int endB,
      List<BaseEdit> edits) {
    this.beginA = beginA;
    this.endA = endA;
    this.beginB = beginB;
    this.endB = endB;
    this.edits = edits;
  }

  protected LineEdit() {
  }

  public LineEdit(Edit edit) {
    this(edit.getBeginA(), edit.getEndA(), edit.getBeginB(), edit.getEndB());
  }

  public LineEdit(BaseEdit edit, List<BaseEdit> edits) {
    this(edit.beginA, edit.endA, edit.beginB, edit.endB, edits);
  }

  public LineEdit(int beginA, int endA, int beginB, int endB) {
    this(beginA, endA, beginB, endB, null);
  }

  public LineEdit(int beginA, int beginB) {
    this(beginA, beginA, beginB, beginB);
  }

  public List<BaseEdit> getEdits() {
    return edits;
  }
}
