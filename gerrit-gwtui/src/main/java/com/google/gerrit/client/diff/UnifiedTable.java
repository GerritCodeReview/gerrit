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

import com.google.gerrit.client.DiffObject;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTMLPanel;

/**
 * A table with one row and one column to hold a unified CodeMirror displaying the files to be
 * compared.
 */
class UnifiedTable extends DiffTable {
  interface Binder extends UiBinder<HTMLPanel, UnifiedTable> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  interface DiffTableStyle extends CssResource {
    String intralineInsert();

    String intralineDelete();

    String diffInsert();

    String diffDelete();

    String unifiedLineNumber();

    String unifiedLineNumberEmpty();

    String lineNumbersLeft();

    String lineNumbersRight();
  }

  private Unified parent;
  @UiField Element cm;
  @UiField static DiffTableStyle style;

  UnifiedTable(Unified parent, DiffObject base, DiffObject revision, String path) {
    super(parent, base, revision, path);

    initWidget(uiBinder.createAndBindUi(this));
    this.parent = parent;
  }

  @Override
  void setHideEmptyPane(boolean hide) {}

  @Override
  boolean isVisibleA() {
    return true;
  }

  @Override
  Unified getDiffScreen() {
    return parent;
  }

  @Override
  int getHeaderHeight() {
    int h = patchSetSelectBoxA.getOffsetHeight() + patchSetSelectBoxB.getOffsetHeight();
    if (hasHeader()) {
      h += diffHeaderRow.getOffsetHeight();
    }
    return h;
  }
}
