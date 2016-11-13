// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ImageResource;

/** Resources used by diff. */
interface Resources extends ClientBundle {
  Resources I = GWT.create(Resources.class);

  @Source("CommentBox.css")
  CommentBox.Style style();

  @Source("Scrollbar.css")
  Scrollbar.Style scrollbarStyle();

  @Source("DiffTable.css")
  DiffTable.Style diffTableStyle();

  /** tango icon library (public domain): http://tango.freedesktop.org/Tango_Icon_Library */
  @Source("goPrev.png")
  ImageResource goPrev();

  @Source("goNext.png")
  ImageResource goNext();

  @Source("goUp.png")
  ImageResource goUp();
}
