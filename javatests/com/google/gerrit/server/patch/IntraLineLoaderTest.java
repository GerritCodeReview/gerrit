// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.jgit.diff.ReplaceEdit;
import org.eclipse.jgit.diff.Edit;
import org.junit.Test;

public class IntraLineLoaderTest {

  @Test
  public void convertsGitilesReplaceEditToGerritReplaceEdit() {
    String a = "abc1\n";
    String b = "def1\n";
    Edit lines = new Edit(0, 1, 0, 1);

    IntraLineDiff diff = compute(a, b, lines);

    assertThat(diff.getStatus()).isEqualTo(IntraLineDiff.Status.EDIT_LIST);
    ImmutableList<Edit> edits = diff.getEdits();
    assertThat(edits).hasSize(1);
    assertThat(edits.get(0)).isInstanceOf(ReplaceEdit.class);

    ReplaceEdit edit = (ReplaceEdit) edits.get(0);
    assertThat(edit.getBeginA()).isEqualTo(lines.getBeginA());
    assertThat(edit.getEndA()).isEqualTo(lines.getEndA());
    assertThat(edit.getBeginB()).isEqualTo(lines.getBeginB());
    assertThat(edit.getEndB()).isEqualTo(lines.getEndB());
    assertThat(edit.getInternalEdits()).isEqualTo(ImmutableList.of(new Edit(0, 3, 0, 3)));
  }

  @Test
  public void keepsInsertLineEditsUnwrapped() {
    String a = "abc\n";
    String b = "abc\ndef\n";
    Edit lines = new Edit(1, 1, 1, 2);

    IntraLineDiff diff = compute(a, b, lines);

    assertThat(diff.getStatus()).isEqualTo(IntraLineDiff.Status.EDIT_LIST);
    assertThat(diff.getEdits()).isEqualTo(ImmutableList.of(lines));
    assertThat(diff.getEdits().get(0)).isNotInstanceOf(ReplaceEdit.class);
  }

  private static IntraLineDiff compute(String a, String b, Edit lines) {
    return IntraLineLoader.compute(
        new Text(a.getBytes(UTF_8)),
        new Text(b.getBytes(UTF_8)),
        ImmutableList.of(lines),
        ImmutableSet.of());
  }
}
