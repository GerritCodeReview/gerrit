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
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;

public interface GerritResources extends ClientBundle {
  @Source("gerrit.css")
  GerritCss css();

  @Source("gwt_override.css")
  CssResource gwt_override();

  @Source("arrowRight.gif")
  public ImageResource arrowRight();

  @Source("editText.png")
  public ImageResource edit();

  @Source("starOpen.gif")
  public ImageResource starOpen();

  @Source("starFilled.gif")
  public ImageResource starFilled();

  @Source("greenCheck.png")
  public ImageResource greenCheck();

  @Source("redNot.png")
  public ImageResource redNot();

  @Source("downloadIcon.png")
  public ImageResource downloadIcon();

  @Source("queryIcon.png")
  public ImageResource queryIcon();

  @Source("addFileComment.png")
  public ImageResource addFileComment();

  @Source("diffy.png")
  public ImageResource gerritAvatar();

  @Source("draftComments.png")
  public ImageResource draftComments();
}
