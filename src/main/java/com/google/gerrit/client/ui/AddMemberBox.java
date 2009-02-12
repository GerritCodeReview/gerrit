package com.google.gerrit.client.ui;

import com.google.gerrit.client.admin.Util;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusListenerAdapter;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

public class AddMemberBox extends Composite {
  
  private final FlowPanel addPanel;
  private final Button addMember;
  private final TextBox nameTxtBox;
  private final SuggestBox nameTxt;
  
  public AddMemberBox() {
    addPanel = new FlowPanel();
    addMember = new Button(Util.C.buttonAddGroupMember());
    nameTxtBox = new TextBox();
    nameTxt = new SuggestBox(new AccountSuggestOracle(), nameTxtBox);
    
    nameTxtBox.setVisibleLength(50);
    nameTxtBox.setText(Util.C.defaultAccountName());
    nameTxtBox.addStyleName("gerrit-InputFieldTypeHint");
    nameTxtBox.addFocusListener(new FocusListenerAdapter() {
      @Override
      public void onFocus(Widget sender) {
        if (Util.C.defaultAccountName().equals(nameTxtBox.getText())) {
          nameTxtBox.setText("");
          nameTxtBox.removeStyleName("gerrit-InputFieldTypeHint");
        }
      }

      @Override
      public void onLostFocus(Widget sender) {
        if ("".equals(nameTxtBox.getText())) {
          nameTxtBox.setText(Util.C.defaultAccountName());
          nameTxtBox.addStyleName("gerrit-InputFieldTypeHint");
        }
      }
    });
    
    addPanel.setStyleName("gerrit-ProjectWatchPanel-AddPanel");
    addPanel.add(nameTxt);
    addPanel.add(addMember);
    
    initWidget(addPanel);
  }
  
  public void addClickListener(ClickListener listener) {
    addMember.addClickListener(listener);
  }
  
  public String getText() {
    String s = nameTxtBox.getText();
    if (s == null || s.equals(Util.C.defaultAccountName())) {
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

}
