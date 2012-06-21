package com.google.gerrit.client.patches;

import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.SimplePanel;

public class PatchTableHeader extends Composite {
  interface Binder extends UiBinder<HTMLPanel, PatchTableHeader> {
  }

  private static Binder uiBinder = GWT.create(Binder.class);

  @UiField
  SimplePanel sideAPanel;

  @UiField
  SimplePanel sideBPanel;

  @UiField
  DivElement aligner;

  PatchSetSelectBox listA;
  PatchSetSelectBox listB;

  public PatchTableHeader() {
    listA = new PatchSetSelectBox(PatchSetSelectBox.Side.A);
    listB = new PatchSetSelectBox(PatchSetSelectBox.Side.B);

    initWidget(uiBinder.createAndBindUi(this));

    sideAPanel.add(listA);
    sideBPanel.add(listB);
  }


  public void display(PatchScript script, final Patch.Key patchKey, final PatchSet.Id idSideA, final PatchSet.Id idSideB, PatchScreen.Type type) {
    listA.display(script, patchKey, idSideA, idSideB, type);
    listB.display(script, patchKey, idSideA, idSideB, type);

    if (type == PatchScreen.Type.UNIFIED) {
      aligner.getStyle().setDisplay(Display.NONE);
    }
  }
}
