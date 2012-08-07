// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.client.patches;

import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiTemplate;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.SimplePanel;

public class PatchTableHeader extends Composite {

  @UiTemplate("PatchTableHeaderSideBySide.ui.xml")
  interface SideBySideBinder extends UiBinder<HTMLPanel, PatchTableHeader> {
  }

  @UiTemplate("PatchTableHeaderUnified.ui.xml")
  interface UnifiedBinder extends UiBinder<HTMLPanel, PatchTableHeader> {
  }

  private static SideBySideBinder uiBinderS = GWT.create(SideBySideBinder.class);
  private static UnifiedBinder uiBinderU = GWT.create(UnifiedBinder.class);

  @UiField
  SimplePanel sideAPanel;

  @UiField
  SimplePanel sideBPanel;

  PatchSetSelectBox listA;
  PatchSetSelectBox listB;

  public PatchTableHeader(PatchScreen.Type type) {
    listA = new PatchSetSelectBox(PatchSetSelectBox.Side.A, type);
    listB = new PatchSetSelectBox(PatchSetSelectBox.Side.B, type);

    if (type == PatchScreen.Type.SIDE_BY_SIDE) {
      initWidget(uiBinderS.createAndBindUi(this));
    } else {
      initWidget(uiBinderU.createAndBindUi(this));
    }

    sideAPanel.add(listA);
    sideBPanel.add(listB);
  }


  public void display(final PatchSetDetail detail, PatchScript script,
      final Patch.Key patchKey, final PatchSet.Id idSideA,
      final PatchSet.Id idSideB, DoubleClickHandler patchSideAClickHandler,
      DoubleClickHandler patchSideBClickHandler) {
    listA.display(detail, script, patchKey, idSideA, idSideB,
        patchSideAClickHandler);
    listB.display(detail, script, patchKey, idSideA, idSideB,
        patchSideBClickHandler);
  }
}
