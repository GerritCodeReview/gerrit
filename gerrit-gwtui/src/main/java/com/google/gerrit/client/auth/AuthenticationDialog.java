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

import com.google.gerrit.client.Gerrit;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.FormPanel.SubmitCompleteEvent;
import com.google.gwt.user.client.ui.FormPanel.SubmitCompleteHandler;
import com.google.gwt.user.client.ui.TabBar;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.user.client.AutoCenterDialogBox;

import java.util.List;

public class AuthenticationDialog extends AutoCenterDialogBox {
  private static final String AUTH_OK = "OK</pre>";

  public AuthenticationDialog(List<String> authPages) {
    super(true, true);
    if (authPages.size() > 1) {
      handleMultiAuthentication(authPages);
    }  else {
      handleSingleAuthentication(authPages);
    }
    setGlassEnabled(true);
    setText(Gerrit.C.signInDialogTitle());
  }

  @Override
  public void show() {
    super.show();
    GlobalKey.dialog(this);
  }

  private void handleSingleAuthentication(List<String> authPages) {
    VerticalPanel container = new VerticalPanel();
    FormPanel authForm = authForm(authPages.get(0));
    container.add(authForm);
    container.add(submit(authForm));
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
      tab.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          for (int i = 0; i < content.getWidgetCount(); i++) {
            content.getWidget(i).setVisible(false);
          }
          int toBeShown = content.getWidgetIndex(formContainer);
          content.getWidget(toBeShown).setVisible(true);
        }
      });
      FormPanel form = authForm(authPage);
      formContainer.add(form);
      formContainer.add(submit(form));

      tabs.addTab(tab);
      content.add(formContainer);
    }

    tabs.selectTab(0);
    content.getWidget(0).setVisible(true);
    add(container);
  }

  private FormPanel authForm(String authName) {
    FormPanel form = new FormPanel();
    form.setAction("/authenticate");
    form.setMethod(FormPanel.METHOD_POST);
    Element authContainer = Document.get().getElementById("gerrit_auth_" + authName);
    form.getElement().appendChild(authContainer);
    form.addSubmitCompleteHandler(new SubmitCompleteHandler() {
      @Override
      public void onSubmitComplete(SubmitCompleteEvent event) {
        String results = event.getResults();
        if (results != null && results.endsWith(AUTH_OK)) {
          Window.Location.reload();
        }
      }
    });

    return form;
  }

  private Button submit(final FormPanel form) {
    return new Button("Submit", new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        form.submit();
      }
    });
  }
}
