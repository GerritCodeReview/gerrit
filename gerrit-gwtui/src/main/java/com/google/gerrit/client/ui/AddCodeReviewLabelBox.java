package com.google.gerrit.client.ui;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.admin.Util;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwtexpui.globalkey.client.NpTextBox;

public class AddCodeReviewLabelBox extends Composite {
  private final FlowPanel addPanel;
  private final Button addMember;
  private final NpTextBox nameTxtBox;

  private final static String LABELS_EXAMPLE =
      "Verified CodeReview-0 CodeReview+1 DrNo";

  public AddCodeReviewLabelBox() {
    addPanel = new FlowPanel();
    addMember = new Button(Util.C.buttonAddGroupMember());
    nameTxtBox = new NpTextBox();

    nameTxtBox.setVisibleLength(50);
    nameTxtBox.setText(LABELS_EXAMPLE);
    nameTxtBox.addStyleName(Gerrit.RESOURCES.css().inputFieldTypeHint());
    nameTxtBox.addFocusHandler(new FocusHandler() {
      @Override
      public void onFocus(final FocusEvent event) {
        if (LABELS_EXAMPLE.equals(nameTxtBox.getText())) {
          nameTxtBox.setText("");
          nameTxtBox.removeStyleName(Gerrit.RESOURCES.css()
              .inputFieldTypeHint());
        }
      }
    });
    nameTxtBox.addBlurHandler(new BlurHandler() {
      @Override
      public void onBlur(final BlurEvent event) {
        if ("".equals(nameTxtBox.getText())) {
          nameTxtBox.setText(LABELS_EXAMPLE);
          nameTxtBox.addStyleName(Gerrit.RESOURCES.css().inputFieldTypeHint());
        }
      }
    });
    nameTxtBox.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (event.getCharCode() == KeyCodes.KEY_ENTER) {
          doAdd();
        }
      }
    });

    addPanel.add(nameTxtBox);
    addPanel.add(addMember);

    initWidget(addPanel);
  }

  public void setAddButtonText(final String text) {
    addMember.setText(text);
  }

  public void addClickHandler(final ClickHandler handler) {
    addMember.addClickHandler(handler);
  }

  public String getText() {
    String s = nameTxtBox.getText();
    if (s == null || s.equals(LABELS_EXAMPLE)) {
      s = "";
    }
    return s;
  }

  public void setEnabled(boolean enabled) {
    addMember.setEnabled(enabled);
    nameTxtBox.setEnabled(enabled);
  }

  public void setText(String text) {
    nameTxtBox.setText(text);
  }

  private void doAdd() {
    addMember.fireEvent(new ClickEvent() {});
  }

}
