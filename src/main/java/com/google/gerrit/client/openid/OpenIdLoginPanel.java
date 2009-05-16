// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.client.SignInDialog;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.FormElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.AbstractImagePrototype;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.FormSubmitCompleteEvent;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Hidden;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.FormPanel.SubmitEvent;
import com.google.gwtexpui.globalkey.client.NpTextBox;

import java.util.Map;

public class OpenIdLoginPanel extends Composite implements
    FormPanel.SubmitHandler {
  private final SignInDialog.Mode mode;
  private final LoginIcons icons;
  private final FlowPanel panelWidget;
  private final FormPanel form;
  private final FlowPanel formBody;
  private final FormPanel redirectForm;
  private final FlowPanel redirectBody;

  private FlowPanel errorLine;
  private InlineLabel errorMsg;

  private Button login;
  private NpTextBox providerId;
  private CheckBox rememberId;
  private boolean discovering;

  public OpenIdLoginPanel(final SignInDialog.Mode m) {
    mode = m;

    icons = GWT.create(LoginIcons.class);

    formBody = new FlowPanel();
    formBody.setStyleName("gerrit-OpenID-loginform");

    form = new FormPanel();
    form.setMethod(FormPanel.METHOD_GET);
    form.addSubmitHandler(this);
    form.add(formBody);

    redirectBody = new FlowPanel();
    redirectBody.setVisible(false);
    redirectForm = new FormPanel();
    redirectForm.add(redirectBody);

    panelWidget = new FlowPanel();
    panelWidget.add(form);
    panelWidget.add(redirectForm);
    initWidget(panelWidget);

    createHeaderLogo();
    createHeaderText();
    createErrorBox();
    createIdentBox();

    link(OpenIdUtil.URL_GOOGLE, OpenIdUtil.C.nameGoogle(), icons.iconGoogle());
    link(OpenIdUtil.URL_YAHOO, OpenIdUtil.C.nameYahoo(), icons.iconYahoo());

    formBody.add(new HTML(OpenIdUtil.C.whatIsOpenIDHtml()));
  }

  public void setFocus(final boolean take) {
    if (take) {
      providerId.selectAll();
    }
    providerId.setFocus(take);
  }

  private void createHeaderLogo() {
    final FlowPanel headerLogo = new FlowPanel();
    headerLogo.setStyleName("gerrit-OpenID-logobox");
    headerLogo.add(icons.openidLogo().createImage());
    formBody.add(headerLogo);
  }

  private void createHeaderText() {
    final FlowPanel headerText = new FlowPanel();
    final String me = Window.Location.getHostName();
    final SmallHeading headerLabel = new SmallHeading();
    switch (mode) {
      case LINK_IDENTIY:
        headerLabel.setText(OpenIdUtil.M.linkAt(me));
        break;
      case SIGN_IN:
      default:
        headerLabel.setText(OpenIdUtil.M.signInAt(me));
        break;
    }
    headerText.add(headerLabel);
    formBody.add(headerText);
  }

  private void createErrorBox() {
    errorLine = new FlowPanel();
    errorLine.setVisible(false);

    errorMsg = new InlineLabel(OpenIdUtil.C.notSupported());
    errorLine.setStyleName("gerrit-OpenID-errorline");
    errorLine.add(errorMsg);
    formBody.add(errorLine);
  }

  private void showError() {
    errorLine.setVisible(true);
  }

  private void hideError() {
    errorLine.setVisible(false);
  }

  private void createIdentBox() {
    final FlowPanel group = new FlowPanel();
    group.setStyleName("gerrit-OpenID-loginline");

    final FlowPanel line1 = new FlowPanel();
    group.add(line1);

    providerId = new NpTextBox();
    providerId.setVisibleLength(60);
    providerId.setStyleName("gerrit-OpenID-openid_identifier");
    providerId.setTabIndex(0);
    providerId.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(final KeyPressEvent event) {
        if (event.getCharCode() == KeyCodes.KEY_ENTER) {
          event.preventDefault();
          form.submit();
        }
      }
    });
    line1.add(providerId);

    login = new Button();
    switch (mode) {
      case LINK_IDENTIY:
        login.setText(OpenIdUtil.C.buttonLinkId());
        break;
      case SIGN_IN:
      default:
        login.setText(OpenIdUtil.C.buttonSignIn());
        break;
    }
    login.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        form.submit();
      }
    });
    login.setTabIndex(2);
    line1.add(login);

    if (mode == SignInDialog.Mode.SIGN_IN) {
      rememberId = new CheckBox(OpenIdUtil.C.rememberMe());
      rememberId.setTabIndex(1);
      group.add(rememberId);

      final String last = Cookies.getCookie(OpenIdUtil.LASTID_COOKIE);
      if (last != null && !"".equals(last)) {
        providerId.setText(last);
        rememberId.setValue(true);
      }
    }

    formBody.add(group);
  }

  private void link(final String identUrl, final String who,
      final AbstractImagePrototype icon) {
    final ClickHandler i = new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        if (!discovering) {
          providerId.setText(identUrl);
          form.submit();
        }
      }
    };

    final FlowPanel line = new FlowPanel();
    line.addStyleName("gerrit-OpenID-directlink");

    final Image img = icon.createImage();
    img.addClickHandler(i);
    line.add(img);

    final InlineLabel lbl = new InlineLabel();
    switch (mode) {
      case LINK_IDENTIY:
        lbl.setText(OpenIdUtil.M.linkWith(who));
        break;
      case SIGN_IN:
      default:
        lbl.setText(OpenIdUtil.M.signInWith(who));
        break;
    }
    lbl.addClickHandler(i);
    line.add(lbl);

    formBody.add(line);
  }

  private void enable(final boolean on) {
    providerId.setEnabled(on);
    login.setEnabled(on);
  }

  private void onDiscovery(final DiscoveryResult result) {
    discovering = false;

    if (result.validProvider) {
      redirectForm.setMethod(FormPanel.METHOD_POST);
      redirectForm.setAction(result.providerUrl);
      redirectBody.clear();
      for (final Map.Entry<String, String> e : result.providerArgs.entrySet()) {
        redirectBody.add(new Hidden(e.getKey(), e.getValue()));
      }

      // The provider won't support operation inside an IFRAME, so we
      // replace our entire application. No fancy waits are needed,
      // the browser won't update anything until its started to load
      // the provider's page.
      //
      FormElement.as(redirectForm.getElement()).setTarget("_top");
      redirectForm.submit();

    } else {
      // We failed discovery. We have to use a deferred command here
      // as we are being called from within an invisible IFRAME. Jump
      // back to the main event loop in the parent window.
      //
      onDiscoveryFailure();
    }
  }

  private void onDiscoveryFailure() {
    showError();
    enable(true);
    providerId.selectAll();
    providerId.setFocus(true);
  }

  @Override
  public void onSubmit(final SubmitEvent event) {
    event.cancel();

    final String openidIdentifier = providerId.getText();
    if (openidIdentifier == null || openidIdentifier.equals("")) {
      enable(true);
      return;
    }

    discovering = true;
    enable(false);
    hideError();

    final boolean remember = rememberId != null && rememberId.getValue();
    final String token = History.getToken();
    OpenIdUtil.SVC.discover(openidIdentifier, mode, remember, token,
        new GerritCallback<DiscoveryResult>() {
          public void onSuccess(final DiscoveryResult result) {
            onDiscovery(result);
          }

          @Override
          public void onFailure(final Throwable caught) {
            super.onFailure(caught);
            onDiscoveryFailure();
          }
        });
  }

  public void onSubmitComplete(final FormSubmitCompleteEvent event) {
  }
}
