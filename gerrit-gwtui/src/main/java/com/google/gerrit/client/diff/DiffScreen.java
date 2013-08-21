//Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.diff;

import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.changes.Side;
import com.google.gwt.core.client.JsArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


abstract class DiffScreen extends Screen {

  abstract void resizePaddingOnOtherSide(DisplaySide mySide, int line);

  abstract DraftBox addDraftBox(CommentInfo info, DisplaySide side);

  abstract void removeDraft(DraftBox box, int line);

  abstract CommentInfo createReply(CommentInfo info);

  abstract void addFileCommentBox(CommentBox box);

  abstract void removeFileCommentBox(DraftBox box);

  abstract Side getStoredSideFromDisplaySide(DisplaySide side);

  abstract void resizeCodeMirror();

  List<CommentInfo> sortComment(JsArray<CommentInfo> unsorted) {
    List<CommentInfo> sorted = new ArrayList<CommentInfo>();
    for (int i = 0; i < unsorted.length(); i++) {
      sorted.add(unsorted.get(i));
    }
    Collections.sort(sorted, new Comparator<CommentInfo>() {
      @Override
      public int compare(CommentInfo o1, CommentInfo o2) {
        return o1.updated().compareTo(o2.updated());
      }
    });
    return sorted;
  }
}