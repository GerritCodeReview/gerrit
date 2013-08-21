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

/** A table holding a unified diff view */
class UnifiedTable2 extends DiffTable {
  interface Binder extends UiBinder<HTMLPanel, UnifiedTable2> {}
  private static Binder uiBinder = GWT.create(Binder.class);

  interface DiffTableStyle extends CssResource {
    String intralineInsert();
    String intralineDelete();
    String diffInsert();
    String diffDelete();
    String activeLine();
    String lineNumbersLeft();
    String lineNumbersRight();
    String lineNumber();
  }

  @UiField
  Element cm;

  @UiField
  static DiffTableStyle style;

  UnifiedTable2(Unified2 host, PatchSet.Id base, PatchSet.Id revision, String path) {
    super(host, base, revision, path);
    initWidget(uiBinder.createAndBindUi(this));
  }

  int getHeaderHeight() {
    return 2 * fileCommentRow.getOffsetHeight() + 2 * patchSetSelectBoxA.getOffsetHeight();
  }
}
