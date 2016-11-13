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

package com.google.gerrit.client;

import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ImageResource;

public interface Resources extends ClientBundle {
  /** silk icons (CC-BY3.0): http://famfamfam.com/lab/icons/silk/ */
  @Source("note_add.png")
  ImageResource addFileComment();

  @Source("tag_blue_add.png")
  ImageResource addHashtag();

  @Source("user_add.png")
  ImageResource addUser();

  @Source("user_edit.png")
  ImageResource editUser();

  // derived from resultset_next.png
  @Source("resultset_down_gray.png")
  ImageResource arrowDown();

  // derived from resultset_next.png
  @Source("resultset_next_gray.png")
  ImageResource arrowRight();

  // derived from resultset_next.png
  @Source("resultset_up_gray.png")
  ImageResource arrowUp();

  @Source("lightbulb.png")
  ImageResource blame();

  @Source("page_white_put.png")
  ImageResource downloadIcon();

  // derived from comment.png
  @Source("comment_draft.png")
  ImageResource draftComments();

  @Source("page_edit.png")
  ImageResource edit();

  @Source("arrow_undo.png")
  ImageResource editUndo();

  @Source("cog.png")
  ImageResource gear();

  @Source("tick.png")
  ImageResource greenCheck();

  @Source("tag_blue.png")
  ImageResource hashtag();

  @Source("lightbulb.png")
  ImageResource info();

  @Source("find.png")
  ImageResource queryIcon();

  @Source("lock.png")
  ImageResource readOnly();

  @Source("cross.png")
  ImageResource redNot();

  @Source("disk.png")
  ImageResource save();

  @Source("star.png")
  ImageResource starFilled();

  // derived from star.png
  @Source("star-open.png")
  ImageResource starOpen();

  @Source("exclamation.png")
  ImageResource warning();

  @Source("help.png")
  ImageResource question();

  /** tango icon library (public domain): http://tango.freedesktop.org/Tango_Icon_Library */
  @Source("goNext.png")
  ImageResource goNext();

  @Source("goPrev.png")
  ImageResource goPrev();

  @Source("goUp.png")
  ImageResource goUp();

  @Source("listAdd.png")
  ImageResource listAdd();

  // derived from important.png
  @Source("merge.png")
  ImageResource merge();

  /** contributed by the artist under Apache2.0 */
  @Source("sideBySideDiff.png")
  ImageResource sideBySideDiff();

  @Source("unifiedDiff.png")
  ImageResource unifiedDiff();

  /** contributed by the artist under CC-BY3.0 */
  @Source("diffy26.png")
  ImageResource gerritAvatar26();
}
