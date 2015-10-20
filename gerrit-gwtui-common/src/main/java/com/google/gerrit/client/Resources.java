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
  @Source("addFileComment.png")
  ImageResource addFileComment();

  @Source("arrowDown.png")
  ImageResource arrowDown();

  @Source("arrowRight.png")
  ImageResource arrowRight();

  @Source("arrowUp.png")
  ImageResource arrowUp();

  @Source("info.png")
  public ImageResource blame();

  @Source("deleteHover.png")
  ImageResource deleteHover();

  @Source("deleteNormal.png")
  ImageResource deleteNormal();

  @Source("diffy26.png")
  ImageResource gerritAvatar26();

  @Source("downloadIcon.png")
  ImageResource downloadIcon();

  @Source("draftComments.png")
  ImageResource draftComments();

  @Source("editText.png")
  ImageResource edit();

  @Source("editUndo.png")
  ImageResource editUndo();

  @Source("gear.png")
  ImageResource gear();

  @Source("goNext.png")
  ImageResource goNext();

  @Source("goPrev.png")
  ImageResource goPrev();

  @Source("goUp.png")
  ImageResource goUp();

  @Source("greenCheck.png")
  ImageResource greenCheck();

  @Source("info.png")
  ImageResource info();

  @Source("listAdd.png")
  ImageResource listAdd();

  @Source("mediaFloppy.png")
  ImageResource save();

  @Source("merge.png")
  ImageResource merge();

  @Source("queryIcon.png")
  ImageResource queryIcon();

  @Source("readOnly.png")
  ImageResource readOnly();

  @Source("redNot.png")
  ImageResource redNot();

  @Source("sideBySideDiff.png")
  ImageResource sideBySideDiff();

  @Source("starFilled.png")
  ImageResource starFilled();

  @Source("starOpen.png")
  ImageResource starOpen();

  @Source("undoNormal.png")
  ImageResource undoNormal();

  @Source("unifiedDiff.png")
  ImageResource unifiedDiff();

  @Source("warning.png")
  ImageResource warning();

  @Source("question.png")
  ImageResource question();
}
