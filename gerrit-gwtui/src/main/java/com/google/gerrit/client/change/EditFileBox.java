package com.google.gerrit.client.change;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.changes.ChangeFileApi;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;

public class EditFileBox extends EditMessageBox {

  private PatchSet.Id id;
  private String file;

  EditFileBox(
      PatchSet.Id id,
      String content,
      String file) {
    super(null, null, content);
    this.id = id;
    this.file = file;
  }

  @Override
  @UiHandler("save")
  void onSave(ClickEvent e) {
    ChangeFileApi.putContent(id, file, message.getText().trim(),
        new AsyncCallback<VoidResult>() {
          @Override
          public void onSuccess(VoidResult result) {
            Gerrit.display(PageLinks.toChange(id.getParentKey()));
            hide();
          }

          @Override
          public void onFailure(Throwable caught) {
          }
        });
  }
}
