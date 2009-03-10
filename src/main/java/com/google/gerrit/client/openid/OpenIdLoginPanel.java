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
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.AbstractImagePrototype;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FormHandler;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.FormSubmitCompleteEvent;
import com.google.gwt.user.client.ui.FormSubmitEvent;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Hidden;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.KeyboardListenerAdapter;
import com.google.gwt.user.client.ui.NamedFrame;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtjsonrpc.client.CallbackHandle;

import java.util.Map;

public class OpenIdLoginPanel extends Composite implements FormHandler {
  private final SignInDialog.Mode mode;
  private final CallbackHandle<?> discoveryCallback;
  private final CallbackHandle<?> signInCallback;
  private final LoginIcons icons;
  private final AllowFrameImpl allowFrame;
  private final FlowPanel panelWidget;
  private final FormPanel form;
  private final FlowPanel formBody;
  private final NamedFrame providerFrame;
  private final Hidden discoveryCbField;
  private final Hidden signInCbField;
  private final Hidden providerField;

  private final FormPanel redirectForm;
  private final FlowPanel redirectBody;

  private FlowPanel errorLine;
  private InlineLabel errorMsg;

  private Button login;
  private TextBox providerId;
  private CheckBox rememberId;
  private boolean discovering;

  public OpenIdLoginPanel(final SignInDialog.Mode m, final CallbackHandle<?> sc) {
    mode = m;
    signInCallback = sc;

    discoveryCallback =
        OpenIdUtil.SVC.discover(new GerritCallback<DiscoveryResult>() {
          public void onSuccess(final DiscoveryResult result) {
            onDiscovery(result);
          }
        });
    icons = GWT.create(LoginIcons.class);
    allowFrame = GWT.create(AllowFrameImpl.class);

    formBody = new FlowPanel();
    formBody.setStyleName("gerrit-OpenID-loginform");
    formBody.add(providerField = new Hidden(OpenIdUtil.OPENID_IDENTIFIER));
    formBody.add(signInCbField = new Hidden(OpenIdUtil.P_SIGNIN_CB));
    formBody.add(discoveryCbField = new Hidden(OpenIdUtil.P_DISCOVERY_CB));
    formBody.add(new Hidden(OpenIdUtil.P_SIGNIN_MODE, mode.name()));

    providerFrame = new NamedFrame(DOM.createUniqueId());
    providerFrame.setVisible(false);

    form = new FormPanel(providerFrame);
    form.setMethod(FormPanel.METHOD_GET);
    form.setAction(GWT.getModuleBaseURL() + "login");
    form.addFormHandler(this);
    form.add(formBody);

    redirectBody = new FlowPanel();
    redirectBody.setVisible(false);
    redirectForm = new FormPanel();
    redirectForm.add(redirectBody);

    panelWidget = new FlowPanel();
    panelWidget.add(form);
    panelWidget.add(redirectForm);
    panelWidget.add(providerFrame);
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

    providerId = new TextBox();
    providerId.setVisibleLength(60);
    providerId.setStyleName("gerrit-OpenID-openid_identifier");
    providerId.setTabIndex(0);
    providerId.addKeyboardListener(new KeyboardListenerAdapter() {
      @Override
      public void onKeyPress(Widget sender, char keyCode, int modifiers) {
        if (keyCode == KEY_ENTER) {
          final Event event = DOM.eventGetCurrentEvent();
          DOM.eventCancelBubble(event, true);
          DOM.eventPreventDefault(event);
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
    login.addClickListener(new ClickListener() {
      public void onClick(Widget sender) {
        form.submit();
      }
    });
    login.setTabIndex(2);
    line1.add(login);

    if (mode == SignInDialog.Mode.SIGN_IN) {
      rememberId = new CheckBox(OpenIdUtil.C.rememberMe());
      rememberId.setName(OpenIdUtil.P_REMEMBERID);
      rememberId.setTabIndex(1);
      group.add(rememberId);

      final String last = Cookies.getCookie(OpenIdUtil.LASTID_COOKIE);
      if (last != null && !"".equals(last)) {
        providerId.setText(last);
        rememberId.setChecked(true);
      }
    }

    formBody.add(group);
  }

  private void link(final String identUrl, final String who,
      final AbstractImagePrototype icon) {
    final ClickListener i = new ClickListener() {
      public void onClick(Widget sender) {
        if (!discovering) {
          providerId.setText(identUrl);
          form.submit();
        }
      }
    };

    final FlowPanel line = new FlowPanel();
    line.addStyleName("gerrit-OpenID-directlink");

    final Image img = icon.createImage();
    img.addClickListener(i);
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
    lbl.addClickListener(i);
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
      final String url = providerId.getText();

      redirectForm.setMethod(FormPanel.METHOD_POST);
      redirectForm.setAction(result.providerUrl);
      redirectBody.clear();
      for (final Map.Entry<String, String> e : result.providerArgs.entrySet()) {
        redirectBody.add(new Hidden(e.getKey(), e.getValue()));
      }

      final FormElement fe = FormElement.as(redirectForm.getElement());
      if (allowFrame.permit(url)) {
        // The provider will work OK inside an IFRAME, so use it.
        //
        fe.setTarget(providerFrame.getName());

        // The provider page needs time to load. It won't load as fast as
        // we can update the DOM so we delay our DOM update for just long
        // enough that the provider is likely to be loaded.
        //
        new Timer() {
          @Override
          public void run() {
            panelWidget.remove(form);
            providerFrame.setVisible(true);
          }
        }.schedule(250);
      } else {
        // The provider won't support operation inside an IFRAME, so we
        // replace our entire application. No fancy waits are needed,
        // the browser won't update anything until its started to load
        // the provider's page.
        //
        fe.setTarget("_top");
      }
      redirectForm.submit();

    } else {
      // We failed discovery. We have to use a deferred command here
      // as we are being called from within an invisible IFRAME. Jump
      // back to the main event loop in the parent window.
      //
      DeferredCommand.addCommand(new Command() {
        public void execute() {
          showError();
          enable(true);
          providerId.selectAll();
          providerId.setFocus(true);
        }
      });
    }
  }

  @Override
  protected void onUnload() {
    discoveryCallback.cancel();
    super.onUnload();
  }

  public void onSubmit(final FormSubmitEvent event) {
    final String url = providerId.getText();
    if (url == null || url.equals("")) {
      enable(true);
      event.setCancelled(true);
      return;
    }

    discovering = true;
    enable(false);
    hideError();

    discoveryCallback.install();
    discoveryCbField.setValue("parent." + discoveryCallback.getFunctionName());
    providerField.setValue(url);

    if (allowFrame.permit(url)) {
      signInCbField.setValue("parent." + signInCallback.getFunctionName());
    } else {
      // The provider won't work right inside of an IFRAME (or likely isn't
      // going to work within the IFRAME) so we need to replace the whole
      // application and then redirect back to this location.
      //
      signInCbField.setValue("history:" + History.getToken());
    }
  }

  @Override
  public void setWidth(final String width) {
    providerFrame.setWidth(width);
    super.setWidth(width);
  }

  @Override
  public void setHeight(final String height) {
    providerFrame.setHeight(height);
    super.setHeight(height);
  }

  public void onSubmitComplete(final FormSubmitCompleteEvent event) {
  }
}
