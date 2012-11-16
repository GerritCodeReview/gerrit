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
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.FormPanel.SubmitCompleteEvent;
import com.google.gwt.user.client.ui.FormPanel.SubmitCompleteHandler;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.TabBar;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.GlobalKey;
import com.google.gwtexpui.user.client.AutoCenterDialogBox;

import java.util.Map;
import java.util.Map.Entry;

public class AuthenticationDialog extends AutoCenterDialogBox {
  private static final String AUTH_OK = "<pre>OK</pre>";

  public AuthenticationDialog(Map<String, String> authPages) {
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

  private void handleSingleAuthentication(Map<String, String> authPages) {
    VerticalPanel container = new VerticalPanel();
    FormPanel authForm = authForm(authPages.entrySet().iterator().next());
    container.add(authForm);
    container.add(submit(authForm));
    add(container);
  }

  private void handleMultiAuthentication(Map<String, String> authPages) {
    VerticalPanel container = new VerticalPanel();
    TabBar tabs = new TabBar();
    final VerticalPanel content = new VerticalPanel();

    container.add(tabs);
    container.add(content);

    for (Entry<String, String> authPage : authPages.entrySet()) {
      final VerticalPanel formContainer = new VerticalPanel();
      formContainer.setVisible(false);
      Anchor tab = new Anchor(authPage.getKey());
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

  private FormPanel authForm(Entry<String, String> auth) {
    FormPanel form = new FormPanel();
    form.setAction("/authenticate");
    form.add(new HTML(auth.getValue()));
    form.addSubmitCompleteHandler(new SubmitCompleteHandler() {
      @Override
      public void onSubmitComplete(SubmitCompleteEvent event) {
        if (AUTH_OK.equals(event.getResults())) {
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
