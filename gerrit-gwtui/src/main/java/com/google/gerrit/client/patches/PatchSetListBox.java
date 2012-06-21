package com.google.gerrit.client.patches;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.client.ui.ListBox;

import java.util.List;

public class PatchSetListBox extends ListBox implements ChangeHandler {
  List<Patch> history;
  Patch.Key patchKey;
  PatchSet.Id idSideA;
  PatchSet.Id idSideB;
  PatchSet.Id idActive;
  Side side;
  PatchScreen.Type screenType;

  public enum Side{
    A, B
  }

  public PatchSetListBox(Side side) {
    this.side = side;
  }

  public PatchSet.Id getPatchId() {
    if (getSelectedIndex() == 0)
      return null;

    Patch patch = history.get(getSelectedIndex()-1);
    return patch.getKey().getParentKey();
  }

  public void display(List<Patch> patches, Patch.Key key, PatchSet.Id idSideA, PatchSet.Id idSideB, final PatchScreen.Type type) {
    this.history = patches;
    this.patchKey = key;
    this.idSideA = idSideA;
    this.idSideB = idSideB;
    this.idActive = (side == Side.A)?idSideA:idSideB;
    this.screenType = PatchScreen.Type.SIDE_BY_SIDE;

    addItem("Base", "0");
    for(Patch patch : patches) {
      PatchSet.Id psId = patch.getKey().getParentKey();
      addItem("Patch Set " + String.valueOf(psId.get()), String.valueOf(psId.get()));
    }

    if (idActive == null) {
      setSelectedIndex(0);
    } else {
      setSelectedIndex(idActive.get());
    }

    addChangeHandler(this);
  }

  @Override
  public void onChange(ChangeEvent event) {
    if (side == Side.A) {
      idSideA = getPatchId();
    } else {
      idSideB = getPatchId();
    }

    Patch.Key k = new Patch.Key(idSideB, patchKey.get());
    Gerrit.display(Dispatcher.toPatchSideBySide(idSideA, k));

    switch (screenType) {
      case SIDE_BY_SIDE:
        Gerrit.display(Dispatcher.toPatchSideBySide(idSideA, k));
        break;
      case UNIFIED:
        Gerrit.display(Dispatcher.toPatchUnified(idSideA, k));
        break;
    }
  }
}
