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

package com.google.gerrit.client.auth.openid;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.SignInDialog;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.auth.SignInMode;
import com.google.gerrit.common.auth.openid.DiscoveryResult;
import com.google.gerrit.common.auth.openid.OpenIdProviderPattern;
import com.google.gerrit.common.auth.openid.OpenIdUrls;
import com.google.gwt.dom.client.FormElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
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

public class OpenIdSignInDialog extends SignInDialog implements
    FormPanel.SubmitHandler {
  static {
    OpenIdResources.I.css().ensureInjected();
  }

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

  public OpenIdSignInDialog(final SignInMode requestedMode, final String token,
      final String initialErrorMsg) {
    super(requestedMode, token);

    formBody = new FlowPanel();
    formBody.setStyleName(OpenIdResources.I.css().loginForm());

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
    add(panelWidget);

    createHeaderLogo();
    createHeaderText();
    createErrorBox();
    createIdentBox();

    link(OpenIdUrls.URL_GOOGLE, OpenIdUtil.C.nameGoogle(), OpenIdResources.I
        .iconGoogle());
    link(OpenIdUrls.URL_YAHOO, OpenIdUtil.C.nameYahoo(), OpenIdResources.I
        .iconYahoo());

    if (initialErrorMsg != null) {
      showError(initialErrorMsg);
    }
    formBody.add(new HTML(OpenIdUtil.C.whatIsOpenIDHtml()));
  }

  @Override
  public void show() {
    super.show();
    providerId.selectAll();
    DeferredCommand.addCommand(new Command() {
      @Override
      public void execute() {
        providerId.setFocus(true);
      }
    });
  }

  private void createHeaderLogo() {
    final FlowPanel headerLogo = new FlowPanel();
    headerLogo.setStyleName(OpenIdResources.I.css().logo());
    headerLogo.add(new Image(OpenIdResources.I.openidLogo()));
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
      case REGISTER:
        headerLabel.setText(OpenIdUtil.M.registerAt(me));
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
    DOM.setStyleAttribute(errorLine.getElement(), "visibility", "hidden");
    errorLine.setStyleName(OpenIdResources.I.css().error());

    errorMsg = new InlineLabel();
    errorLine.add(errorMsg);
    formBody.add(errorLine);
  }

  private void showError(final String msgText) {
    errorMsg.setText(msgText);
    DOM.setStyleAttribute(errorLine.getElement(), "visibility", "");
  }

  private void hideError() {
    DOM.setStyleAttribute(errorLine.getElement(), "visibility", "hidden");
  }

  private void createIdentBox() {
    boolean remember = mode == SignInMode.SIGN_IN || mode == SignInMode.REGISTER;

    final FlowPanel group = new FlowPanel();
    group.setStyleName(OpenIdResources.I.css().loginLine());

    final FlowPanel line1 = new FlowPanel();
    group.add(line1);

    providerId = new NpTextBox();
    providerId.setVisibleLength(60);
    providerId.setStyleName(OpenIdResources.I.css().identifier());
    providerId.setTabIndex(0);
    providerId.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(final KeyPressEvent event) {
        if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
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
      case REGISTER:
        login.setText(OpenIdUtil.C.buttonRegister());
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
    login.setTabIndex(remember ? 2 : 1);
    line1.add(login);

    Button close = new Button(Gerrit.C.signInDialogClose());
    close.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        hide();
      }
    });
    close.setTabIndex(remember ? 3 : 2);
    line1.add(close);

    if (remember) {
      rememberId = new CheckBox(OpenIdUtil.C.rememberMe());
      rememberId.setTabIndex(1);
      group.add(rememberId);

      String last = Cookies.getCookie(OpenIdUrls.LASTID_COOKIE);
      if (last != null && !"".equals(last)) {
        if (last.startsWith("\"") && last.endsWith("\"")) {
          // Dequote the value. We shouldn't have to do this, but
          // something is causing some Google Account tokens to get
          // wrapped up in double quotes when obtained from the cookie.
          //
          last = last.substring(1, last.length() - 2);
        }
        providerId.setText(last);
        rememberId.setValue(true);
      }
    }

    formBody.add(group);
  }

  private void link(final String identUrl, final String who,
      final ImageResource icon) {
    if (!isAllowedProvider(identUrl)) {
      return;
    }

    final ClickHandler i = new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        event.preventDefault();
        if (!discovering) {
          providerId.setText(identUrl);
          form.submit();
        }
      }
    };

    final FlowPanel line = new FlowPanel();
    line.addStyleName(OpenIdResources.I.css().directLink());

    final Image img = new Image(icon);
    img.addClickHandler(i);
    line.add(img);

    final Anchor text = new Anchor();
    switch (mode) {
      case LINK_IDENTIY:
        text.setText(OpenIdUtil.M.linkWith(who));
        break;
      case REGISTER:
        text.setText(OpenIdUtil.M.registerWith(who));
        break;
      case SIGN_IN:
      default:
        text.setText(OpenIdUtil.M.signInWith(who));
        break;
    }
    text.setHref(identUrl);
    text.addClickHandler(i);
    line.add(text);

    formBody.add(line);
  }

  private static boolean isAllowedProvider(final String identUrl) {
    for (OpenIdProviderPattern p : Gerrit.getConfig().getAllowedOpenIDs()) {
      if (p.matches(identUrl)) {
        return true;
      }
    }
    return false;
  }

  private void enable(final boolean on) {
    providerId.setEnabled(on);
    login.setEnabled(on);
  }

  private void onDiscovery(final DiscoveryResult result) {
    discovering = false;

    switch (result.status) {
      case VALID:
        // The provider won't support operation inside an IFRAME,
        // so we replace our entire application.
        //
        redirectForm.setMethod(FormPanel.METHOD_POST);
        redirectForm.setAction(result.providerUrl);
        redirectBody.clear();
        for (final Map.Entry<String, String> e : result.providerArgs.entrySet()) {
          redirectBody.add(new Hidden(e.getKey(), e.getValue()));
        }
        FormElement.as(redirectForm.getElement()).setTarget("_top");
        redirectForm.submit();
        break;

      case NOT_ALLOWED:
        showError(OpenIdUtil.C.notAllowed());
        enableRetryDiscovery();
        break;

      case NO_PROVIDER:
        showError(OpenIdUtil.C.noProvider());
        enableRetryDiscovery();
        break;

      case ERROR:
      default:
        showError(OpenIdUtil.C.error());
        enableRetryDiscovery();
        break;
    }
  }

  private void enableRetryDiscovery() {
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

    if (!isAllowedProvider(openidIdentifier)) {
      showError(OpenIdUtil.C.notAllowed());
      enableRetryDiscovery();
      return;
    }

    discovering = true;
    enable(false);
    hideError();

    final boolean remember = rememberId != null && rememberId.getValue();
    OpenIdUtil.SVC.discover(openidIdentifier, mode, remember, token,
        new GerritCallback<DiscoveryResult>() {
          public void onSuccess(final DiscoveryResult result) {
            onDiscovery(result);
          }

          @Override
          public void onFailure(final Throwable caught) {
            super.onFailure(caught);
            enableRetryDiscovery();
          }
        });
  }

  public void onSubmitComplete(final FormSubmitCompleteEvent event) {
  }
}
