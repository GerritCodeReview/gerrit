// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.data;

import com.google.gerrit.client.reviewdb.PatchLineComment;

import java.util.ArrayList;
import java.util.List;

public abstract class LineWithComments {
  protected List<PatchLineComment> comments;

  protected LineWithComments() {
  }

  public List<PatchLineComment> getComments() {
    return comments;
  }

  public void addComment(final PatchLineComment plc) {
    if (comments == null) {
      comments = new ArrayList<PatchLineComment>(4);
    }
    comments.add(plc);
  }
}
