package com.google.gerrit.client.patches;

import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.Patch.Key;
import com.google.gwt.user.client.ui.Label;

public class PatchScreenItem {

  private Patch.Key key;
  private AbstractPatchContentTable contentTable;
  private Label noDifference;

  public Patch.Key getKey() {
    return key;
  }

  public void setKey(Patch.Key key) {
    this.key = key;
  }

  public AbstractPatchContentTable getContentTable() {
    return contentTable;
  }

  public void setContentTable(AbstractPatchContentTable contentTable) {
    this.contentTable = contentTable;
  }

  public Label getNoDifference() {
    return noDifference;
  }

  public void setNoDifference(Label noDifference) {
    this.noDifference = noDifference;
  }

  public PatchScreenItem(Key key, AbstractPatchContentTable contentTable,
      Label noDifference) {
    this.key = key;
    this.contentTable = contentTable;
    this.noDifference = noDifference;
  }
}
