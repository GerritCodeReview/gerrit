// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.data.ChangeDetail;
import com.google.gerrit.client.data.PatchSetDetail;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DisclosureEvent;
import com.google.gwt.user.client.ui.DisclosureHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;

class PatchSetPanel extends Composite implements DisclosureHandler {
  private static final int R_DOWNLOAD = 0;
  private static final int R_CNT = 1;

  private final ChangeDetail changeDetail;
  private final PatchSet patchSet;
  private final FlowPanel body;

  private Grid infoTable;
  private PatchTable patchTable;

  PatchSetPanel(final ChangeDetail detail, final PatchSet ps) {
    changeDetail = detail;
    patchSet = ps;
    body = new FlowPanel();
    initWidget(body);
  }

  public void ensureLoaded(final PatchSetDetail detail) {
    infoTable = new Grid(R_CNT, 2);
    infoTable.setStyleName("gerrit-InfoBlock");
    infoTable.addStyleName("gerrit-PatchSetInfoBlock");

    initRow(R_DOWNLOAD, Util.C.patchSetInfoDownload());

    final CellFormatter itfmt = infoTable.getCellFormatter();
    itfmt.addStyleName(0, 0, "topmost");
    itfmt.addStyleName(0, 1, "topmost");
    itfmt.addStyleName(R_CNT - 1, 0, "bottomheader");
    itfmt.addStyleName(R_DOWNLOAD, 1, "command");

    infoTable.setText(R_DOWNLOAD, 1, Util.M.repoDownload(changeDetail
        .getChange().getDest().getParentKey().get(), changeDetail.getChange()
        .getId(), patchSet.getId()));

    patchTable = new PatchTable();
    patchTable.setSavePointerId("patchTable "
        + changeDetail.getChange().getId() + " " + patchSet.getId());
    patchTable.display(detail.getPatches());
    patchTable.finishDisplay(false);

    body.add(infoTable);
    body.add(patchTable);
  }

  public void onOpen(final DisclosureEvent event) {
    if (infoTable == null) {
      Util.DETAIL_SVC.patchSetDetail(patchSet.getKey(),
          new GerritCallback<PatchSetDetail>() {
            public void onSuccess(final PatchSetDetail result) {
              ensureLoaded(result);
            }
          });
    }
  }

  public void onClose(final DisclosureEvent event) {
  }

  private void initRow(final int row, final String name) {
    infoTable.setText(row, 0, name);
    infoTable.getCellFormatter().addStyleName(row, 0, "header");
  }
}
