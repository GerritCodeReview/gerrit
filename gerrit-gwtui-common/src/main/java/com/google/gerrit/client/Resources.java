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
  @Source("arrowRight.png")
  public ImageResource arrowRight();

  @Source("arrowUp.png")
  public ImageResource arrowUp();

  @Source("arrowDown.png")
  public ImageResource arrowDown();

  @Source("editText.png")
  public ImageResource edit();

  @Source("mediaFloppy.png")
  public ImageResource save();

  @Source("starOpen.png")
  public ImageResource starOpen();

  @Source("starFilled.png")
  public ImageResource starFilled();

  @Source("greenCheck.png")
  public ImageResource greenCheck();

  @Source("redNot.png")
  public ImageResource redNot();

  @Source("editUndo.png")
  public ImageResource editUndo();

  @Source("downloadIcon.png")
  public ImageResource downloadIcon();

  @Source("queryIcon.png")
  public ImageResource queryIcon();

  @Source("addFileComment.png")
  public ImageResource addFileComment();

  @Source("diffy26.png")
  public ImageResource gerritAvatar26();

  @Source("draftComments.png")
  public ImageResource draftComments();

  @Source("readOnly.png")
  public ImageResource readOnly();

  @Source("gear.png")
  public ImageResource gear();

  @Source("info.png")
  public ImageResource info();

  @Source("warning.png")
  public ImageResource warning();

  @Source("listAdd.png")
  public ImageResource listAdd();

  @Source("merge.png")
  public ImageResource merge();

  @Source("removeReviewer.png")
  public ImageResource removeReviewer();

  @Source("deleteNormal.png")
  public ImageResource deleteNormal();

  @Source("deleteHover.png")
  public ImageResource deleteHover();

  @Source("undoNormal.png")
  public ImageResource undoNormal();

  @Source("goPrev.png")
  public ImageResource goPrev();

  @Source("goNext.png")
  public ImageResource goNext();

  @Source("goUp.png")
  public ImageResource goUp();
}
