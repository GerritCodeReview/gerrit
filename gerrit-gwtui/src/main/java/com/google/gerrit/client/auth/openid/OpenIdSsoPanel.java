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
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.auth.SignInMode;
import com.google.gerrit.common.auth.openid.DiscoveryResult;
import com.google.gerrit.common.auth.openid.OpenIdUrls;
import com.google.gwt.dom.client.FormElement;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.Hidden;

import java.util.Map;

public class OpenIdSsoPanel extends FlowPanel {
  private final FormPanel redirectForm;
  private final FlowPanel redirectBody;
  private final String ssoUrl;

  public OpenIdSsoPanel() {
    super();
    redirectBody = new FlowPanel();
    redirectBody.setVisible(false);
    redirectForm = new FormPanel();
    redirectForm.add(redirectBody);

    add(redirectForm);

    ssoUrl = Gerrit.getConfig().getOpenIdSsoUrl();
  }

  public void authenticate(SignInMode requestedMode, final String token) {
    OpenIdUtil.SVC.discover(ssoUrl, requestedMode, /* remember */ false, token,
        new GerritCallback<DiscoveryResult>() {
          public void onSuccess(final DiscoveryResult result) {
            onDiscovery(result);
          }

          @Override
          public void onFailure(final Throwable caught) {
            super.onFailure(caught);
          }
        });
  }

  private void onDiscovery(final DiscoveryResult result) {

    switch (result.status) {
      case VALID:
        redirectForm.setMethod(FormPanel.METHOD_POST);
        redirectForm.setAction(result.providerUrl);
        redirectBody.clear();
        for (final Map.Entry<String, String> e : result.providerArgs.entrySet()) {
          redirectBody.add(new Hidden(e.getKey(), e.getValue()));
        }
        FormElement.as(redirectForm.getElement()).setTarget("_top");
        redirectForm.submit();
        break;
    }
  }
}
