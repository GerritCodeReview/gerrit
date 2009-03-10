// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client;

import com.google.gerrit.client.account.SignInResult;
import com.google.gerrit.client.account.SignInResult.Status;
import com.google.gerrit.client.openid.OpenIdLoginPanel;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.user.client.AutoCenterDialogBox;
import com.google.gwtjsonrpc.client.CallbackHandle;

/**
 * Prompts the user to sign in to their account.
 * <p>
 * This dialog performs the login within an iframe, allowing normal HTML based
 * login pages to be used, including those which aren't served from the same
 * server as Gerrit. This is important to permit an OpenID provider or some
 * other web based single-sign-on system to be used for authentication.
 * <p>
 * Post login the iframe content is expected to execute the JavaScript snippet:
 * 
 * <pre>
 * $callback(account);
 * </pre>
 * 
 * where <code>$callback</code> is the parameter in the initial request and
 * <code>account</code> is either <code>!= null</code> (the user is now signed
 * in) or <code>null</code> (the sign in was aborted/canceled before it
 * completed).
 */
public class SignInDialog extends AutoCenterDialogBox {
  public static enum Mode {
    SIGN_IN, LINK_IDENTIY;
  }

  private static SignInDialog current;

  private final Mode mode;
  private final CallbackHandle<SignInResult> signInCallback;
  private final AsyncCallback<?> appCallback;
  private Widget panel;

  /**
   * Create a new dialog to handle user sign in.
   * 
   * @param callback optional; onSuccess will be called if sign is completed.
   *        This can be used to trigger sending an RPC or some other action.
   */
  public SignInDialog(final AsyncCallback<?> callback) {
    this(Mode.SIGN_IN, callback);
  }

  /**
   * Create a new dialog to handle user sign in.
   * 
   * @param signInMode type of mode the login will perform.
   * @param callback optional; onSuccess will be called if sign is completed.
   *        This can be used to trigger sending an RPC or some other action.
   */
  public SignInDialog(final Mode signInMode, final AsyncCallback<?> callback) {
    super(/* auto hide */true, /* modal */true);

    mode = signInMode;
    signInCallback =
        com.google.gerrit.client.account.Util.LOGIN_SVC
            .signIn(new GerritCallback<SignInResult>() {
              public void onSuccess(final SignInResult result) {
                onCallback(result);
              }
            });
    appCallback = callback;

    switch (Common.getGerritConfig().getLoginType()) {
      case OPENID:
        panel = new OpenIdLoginPanel(signInMode, signInCallback);
        break;

      default: {
        final FlowPanel fp = new FlowPanel();
        fp.add(new Label(Gerrit.C.loginTypeUnsupported()));
        panel = fp;
        break;
      }
    }
    add(panel);
    onResize(Window.getClientWidth(), Window.getClientHeight());

    switch (signInMode) {
      case LINK_IDENTIY:
        setText(Gerrit.C.linkIdentityDialogTitle());
        break;
      default:
        setText(Gerrit.C.signInDialogTitle());
        break;
    }
  }

  @Override
  protected void onResize(final int width, final int height) {
    resizeFrame(width, height);
    super.onResize(width, height);
  }

  private void resizeFrame(final int width, final int height) {
    final int w = Math.min(630, width - 15);
    final int h = Math.min(460, height - 60);
    panel.setWidth(w + "px");
    panel.setHeight(h + "px");
  }

  @Override
  public void show() {
    if (current != null) {
      current.hide();
    }

    super.show();

    current = this;
    signInCallback.install();

    if (panel instanceof OpenIdLoginPanel) {
      ((OpenIdLoginPanel) panel).setFocus(true);
    }
  }

  @Override
  protected void onUnload() {
    if (current == this) {
      signInCallback.cancel();
      current = null;
    }
    super.onUnload();
  }

  private void onCallback(final SignInResult result) {
    final Status rc = result.getStatus();
    if (rc == SignInResult.Status.CANCEL) {
      hide();
    } else if (rc == SignInResult.Status.SUCCESS) {
      onSuccess(result);
    } else {
      GWT.log("Unexpected SignInResult.Status " + rc, null);
    }
  }

  private void onSuccess(final SignInResult result) {
    final AsyncCallback<?> ac = appCallback;
    switch (mode) {
      default:
      case LINK_IDENTIY:
        hide();
        if (ac != null) {
          DeferredCommand.addCommand(new Command() {
            public void execute() {
              ac.onSuccess(null);
            }
          });
        }
        break;

      case SIGN_IN:
        com.google.gerrit.client.account.Util.ACCOUNT_SVC
            .myAccount(new GerritCallback<Account>() {
              public void onSuccess(final Account result) {
                DeferredCommand.addCommand(new Command() {
                  public void execute() {
                    hide();
                    Gerrit.postSignIn(result, ac);
                  }
                });
              }

              @Override
              public void onFailure(final Throwable caught) {
                hide();
                super.onFailure(caught);
              }
            });
        break;
    }
  }
}
