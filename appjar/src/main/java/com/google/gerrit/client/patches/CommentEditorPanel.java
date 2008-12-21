package com.google.gerrit.client.patches;

import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;

public class CommentEditorPanel extends Composite implements ClickListener {
  private final PatchLineComment comment;
  private final TextArea text;
  private final Button save;
  private final Button cancel;

  public CommentEditorPanel(final PatchLineComment plc) {
    comment = plc;

    final FlowPanel body = new FlowPanel();
    body.setStyleName("gerrit-CommentEditor");

    text = new TextArea();
    text.setCharacterWidth(60);
    text.setVisibleLines(5);
    body.add(text);

    final FlowPanel buttons = new FlowPanel();
    buttons.setStyleName("gerrit-CommentEditor-Buttons");
    body.add(buttons);

    save = new Button();
    save.setText("Save");
    save.addClickListener(this);
    buttons.add(save);

    cancel = new Button();
    cancel.setText("Cancel");
    cancel.addClickListener(this);
    buttons.add(cancel);

    initWidget(body);
  }

  public void onClick(Widget sender) {
    if (sender == save) {
      onSave();
    } else if (sender == cancel) {
      onCancel();
    }
  }

  void onSave() {
  }

  void onCancel() {
  }
}
