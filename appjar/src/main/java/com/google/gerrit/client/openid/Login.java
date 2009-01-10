// Copyright 2009 Google Inc.
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

package com.google.gerrit.client.openid;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.AbstractImagePrototype;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FormHandler;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.FormSubmitCompleteEvent;
import com.google.gwt.user.client.ui.FormSubmitEvent;
import com.google.gwt.user.client.ui.Hidden;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

import java.util.HashSet;
import java.util.Set;

public class Login implements EntryPoint, FormHandler {
  private static final String URL_YAHOO = "https://me.yahoo.com";
  private static final String URL_GOOGLE =
      "https://www.google.com/accounts/o8/id";

  private Set<String> allowsIFRAME;
  private LoginIcons icons;
  private RootPanel body;
  private FormPanel form;
  private Button login;
  private Hidden in_callback;
  private Hidden in_token;
  private TextBox providerUrl;

  private static native String getLast_openid_identifier()
  /*-{ return $wnd.gerrit_openid_identifier.value; }-*/;

  public void onModuleLoad() {
    allowsIFRAME = new HashSet<String>();
    allowsIFRAME.add(URL_GOOGLE);

    icons = GWT.create(LoginIcons.class);
    body = RootPanel.get("gerrit_login");
    form = FormPanel.wrap(DOM.getElementById("login_form"));
    form.addFormHandler(this);
    in_callback = Hidden.wrap(DOM.getElementById("in_callback"));
    in_token = Hidden.wrap(DOM.getElementById("in_token"));

    createHeaderLogo();
    createHeaderText();
    createErrorBox();
    createIdentBox();

    createSignIn(URL_GOOGLE, Util.C.directGoogle(), icons.iconGoogle());
    createSignIn(URL_YAHOO, Util.C.directYahoo(), icons.iconYahoo());

    providerUrl.setFocus(true);
  }

  private void createHeaderLogo() {
    final FlowPanel headerLogo = new FlowPanel();
    headerLogo.setStyleName("gerrit-OpenID-logobox");
    headerLogo.add(icons.openidLogo().createImage());
    body.add(headerLogo);
  }

  private void createHeaderText() {
    final FlowPanel headerText = new FlowPanel();
    final Label headerLabel =
        new Label(Util.M.signInAt(Window.Location.getHostName()));
    headerLabel.setStyleName("gerrit-SmallHeading");
    headerText.add(headerLabel);
    body.add(headerText);
  }

  private void createErrorBox() {
    final String url = getLast_openid_identifier();
    if (url != null && !url.equals("")) {
      final FlowPanel line = new FlowPanel();
      final InlineLabel msg = new InlineLabel(Util.M.notSupported(url));
      line.setStyleName("gerrit-OpenID-errorline");
      line.add(msg);
      body.add(line);
    }
  }

  private void createIdentBox() {
    final FlowPanel line = new FlowPanel();
    line.setStyleName("gerrit-OpenID-loginline");
    providerUrl = new TextBox();
    providerUrl.setName("openid_identifier");
    providerUrl.setVisibleLength(40);
    providerUrl.setStyleName("gerrit-OpenID-openid_identifier");
    providerUrl.setTabIndex(0);
    line.add(providerUrl);

    login = new Button(Util.C.buttonSignIn(), new ClickListener() {
      public void onClick(Widget sender) {
        form.submit();
      }
    });
    login.setTabIndex(1);
    line.add(login);

    body.add(line);
  }

  private void createSignIn(final String identUrl, final String prompt,
      final AbstractImagePrototype icon) {
    final ClickListener i = new ClickListener() {
      public void onClick(Widget sender) {
        providerUrl.setText(identUrl);
        form.submit();
      }
    };

    final FlowPanel line = new FlowPanel();
    line.addStyleName("gerrit-OpenID-directlink");

    final Image img = icon.createImage();
    img.addClickListener(i);
    line.add(img);

    final InlineLabel lbl = new InlineLabel(prompt);
    lbl.addClickListener(i);
    line.add(lbl);

    body.add(line);
  }

  public void onSubmit(final FormSubmitEvent event) {
    final String url = providerUrl.getText();
    if (url == null || url.equals("")) {
      event.setCancelled(true);
      return;
    }

    if (!GWT.isScript() || !allowsIFRAME.contains(url)) {
      // The hosted mode debugger chokes on our return redirect now,
      // and I cannot figure out why. So we use a top level page
      // instead, even if the site would have allowed it.
      //
      // Not all OpenID providers permit their login pages to be
      // embedded into an IFRAME. Only those that we know work
      // are permitted to stay inside of the IFRAME, everyone else
      // has to use this logic to replace the page with that of the
      // provider, and eventually redirect back to the same anchor.
      //
      DOM.setElementAttribute(form.getElement(), "target", "_top");
      form.setMethod("POST");
      in_callback.setValue("history:" + in_token.getValue());
    }
    login.setEnabled(false);
  }

  public void onSubmitComplete(final FormSubmitCompleteEvent event) {
  }
}
