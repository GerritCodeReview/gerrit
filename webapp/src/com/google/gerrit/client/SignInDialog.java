// Copyright 2008 Google Inc.
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

import com.google.gerrit.client.reviewdb.Account;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.Frame;
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
public class SignInDialog extends DialogBox {
  private static SignInDialog current;

  private final CallbackHandle<Account> signInCallback;
  private final AsyncCallback<?> appCallback;
  private final Frame loginFrame;

  /**
   * Create a new dialog to handle user sign in.
   * 
   * @param callback optional; onSuccess will be called if sign is completed.
   *        This can be used to trigger sending an RPC or some other action.
   */
  public SignInDialog(final AsyncCallback<?> callback) {
    super(/* auto hide */true, /* modal */true);

    signInCallback =
        com.google.gerrit.client.account.Util.LOGIN_SVC
            .signIn(new AsyncCallback<Account>() {
              public void onSuccess(final Account result) {
                onCallback(result);
              }

              public void onFailure(Throwable caught) {
                GWT.log("Unexpected signIn failure", caught);
              }
            });
    appCallback = callback;

    loginFrame = new Frame();
    loginFrame.setWidth("630px");
    loginFrame.setHeight("420px");
    add(loginFrame);
    setText(Gerrit.C.signInDialogTitle());
  }

  @Override
  public void show() {
    if (current != null) {
      current.hide();
    }

    super.show();

    current = this;
    signInCallback.install();

    final StringBuffer url = new StringBuffer();
    url.append(GWT.getModuleBaseURL());
    url.append("login");
    url.append("?");
    url.append("callback=parent." + signInCallback.getFunctionName());
    loginFrame.setUrl(url.toString());
  }

  @Override
  protected void onUnload() {
    if (current == this) {
      signInCallback.cancel();
      current = null;
    }
    super.onUnload();
  }

  private void onCallback(final Account result) {
    if (result != null) {
      Gerrit.postSignIn();
      hide();
      final AsyncCallback<?> ac = appCallback;
      if (ac != null) {
        DeferredCommand.addCommand(new Command() {
          public void execute() {
            ac.onSuccess(null);
          }
        });
      }
    } else {
      hide();
    }
  }
}
