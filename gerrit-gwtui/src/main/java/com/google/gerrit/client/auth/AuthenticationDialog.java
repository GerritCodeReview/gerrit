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

package com.google.gerrit.client.auth;

import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.TabBar;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.user.client.AutoCenterDialogBox;
import java.util.List;

public class AuthenticationDialog extends AutoCenterDialogBox {

  public AuthenticationDialog(List<String> authPages) {
    super(true, true);
    if (authPages.size() == 1) {
      handleSingleAuthentication(authPages.get(0));
    } else if (authPages.size() > 1) {
      handleMultiAuthentication(authPages);
    } else { // authPages.size() == 0
      ErrorDialog d = new ErrorDialog(Gerrit.C.emptyAuthPagesMessage());
      d.setTitle(Gerrit.C.emptyAuthPagesTitle());
      d.center();
      return;
    }
    setGlassEnabled(true);
    setText(Gerrit.C.signInDialogTitle());
  }

  @Override
  public void show() {
    super.show();
    GlobalKey.dialog(this);
    Element userNameField = Document.get().getElementById("username");
    if (userNameField != null) {
      if (Gerrit.getUserAccount() != null && Gerrit.getUserAccount().username() != null) {
        userNameField.setPropertyString("value", Gerrit.getUserAccount().username());
      }
      userNameField.focus();
    }
  }

  private void handleSingleAuthentication(String authName) {
    VerticalPanel container = new VerticalPanel();
    FormPanel formPanel = extractForm(authName);
    container.add(formPanel);
    add(container);
  }

  private void handleMultiAuthentication(List<String> authPages) {
    VerticalPanel container = new VerticalPanel();
    TabBar tabs = new TabBar();
    final VerticalPanel content = new VerticalPanel();

    container.add(tabs);
    container.add(content);

    for (String authPage : authPages) {
      final VerticalPanel formContainer = new VerticalPanel();
      formContainer.setVisible(false);
      Anchor tab = new Anchor(authPage);
      tab.addClickHandler(
          new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
              for (int i = 0; i < content.getWidgetCount(); i++) {
                content.getWidget(i).setVisible(false);
              }
              int toBeShown = content.getWidgetIndex(formContainer);
              content.getWidget(toBeShown).setVisible(true);
            }
          });
      tabs.addTab(tab);
      content.add(extractForm(authPage));
    }

    tabs.selectTab(0);
    content.getWidget(0).setVisible(true);
    add(container);
  }

  private FormPanel extractForm(String authName) {
    Element authForm = Document.get().getElementById("gerrit_auth_" + authName);
    setSubmitButtonLabel(authForm);
    FormPanel formPanel = FormPanel.wrap(authForm);
    return formPanel;
  }

  private void setSubmitButtonLabel(Element authForm) {
    NodeList<Element> inputs = authForm.getElementsByTagName(InputElement.TAG);
    for (int i = 0; i < inputs.getLength(); i++) {
      Element input = inputs.getItem(i);
      if ("submit".equalsIgnoreCase(input.getAttribute("type"))) {
        input.setAttribute("value", Gerrit.C.buttonSignIn());
        return;
      }
    }
  }
}
