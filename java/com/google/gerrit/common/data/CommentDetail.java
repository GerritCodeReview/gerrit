// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.common.data;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.PatchSet;
import java.util.ArrayList;
import java.util.List;

public class CommentDetail {
  protected List<Comment> a;
  protected List<Comment> b;

  private transient PatchSet.Id idA;
  private transient PatchSet.Id idB;

  public CommentDetail(PatchSet.Id idA, PatchSet.Id idB) {
    this.a = new ArrayList<>();
    this.b = new ArrayList<>();
    this.idA = idA;
    this.idB = idB;
  }

  protected CommentDetail() {}

  public void include(Change.Id changeId, Comment p) {
    PatchSet.Id psId = PatchSet.id(changeId, p.key.patchSetId);
    if (p.side == 0) {
      if (idA == null && idB.equals(psId)) {
        a.add(p);
      }
    } else if (p.side == 1) {
      if (idA != null && idA.equals(psId)) {
        a.add(p);
      } else if (idB.equals(psId)) {
        b.add(p);
      }
    }
  }

  public List<Comment> getCommentsA() {
    return a;
  }

  public List<Comment> getCommentsB() {
    return b;
  }

  public boolean isEmpty() {
    return a.isEmpty() && b.isEmpty();
  }
}
