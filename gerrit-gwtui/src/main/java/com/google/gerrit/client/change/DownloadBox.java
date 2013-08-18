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

package com.google.gerrit.client.change;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.download.ChangeDownloadPanel;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;

class DownloadBox extends Composite {
  interface Binder extends UiBinder<Grid, DownloadBox> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField Grid downloadTable;

  DownloadBox(String project, PatchSet.Id patchSetId) {
    initWidget(uiBinder.createAndBindUi(this));
    downloadTable.resize(1, 2);
    downloadTable.setText(0, 0, Util.C.patchSetInfoDownload());
    downloadTable.getCellFormatter().addStyleName(0, 0,
        Gerrit.RESOURCES.css().header());
    final CellFormatter itfmt = downloadTable.getCellFormatter();
    itfmt.addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
    itfmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
    itfmt.addStyleName(0, 1, Gerrit.RESOURCES.css()
        .downloadLinkListCell());
    ChangeDownloadPanel dp =
        new ChangeDownloadPanel(project, patchSetId.toRefName(),
            // TODO(davido): retrieve ChangeDetail.isAllowsAnonymous()
            true,
            patchSetId.getParentKey().get(),
            patchSetId.get());
    downloadTable.setWidget(0, 1, dp);
  }
}
