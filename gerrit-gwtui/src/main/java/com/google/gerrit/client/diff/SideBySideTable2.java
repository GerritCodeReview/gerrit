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

import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTMLPanel;

/**
 * A table with one row and two columns to hold the two CodeMirrors displaying
 * the files to be diffed.
 */
class SideBySideTable2 extends DiffTable {
  interface Binder extends UiBinder<HTMLPanel, SideBySideTable2> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  interface DiffTableStyle extends CssResource {
    String intralineBg();
    String diff();
    String activeLine();
    String hideNumber();
  }

  @UiField
  Element cmA;

  @UiField
  Element cmB;

  @UiField
  static DiffTableStyle style;

  SideBySideTable2(SideBySide2 host, PatchSet.Id base, PatchSet.Id revision, String path) {
    super(host, base, revision, path);
    initWidget(uiBinder.createAndBindUi(this));
  }

  int getHeaderHeight() {
    return fileCommentRow.getOffsetHeight() + patchSetSelectBoxA.getOffsetHeight();
  }
}
