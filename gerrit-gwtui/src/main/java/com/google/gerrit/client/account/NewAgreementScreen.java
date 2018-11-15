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

package com.google.gerrit.client.account;

import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.info.AgreementInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gerrit.client.ui.OnEditEnabler;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.PageLinks;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NewAgreementScreen extends AccountScreen {
  private final String nextToken;
  private Set<String> mySigned;
  private List<AgreementInfo> available;
  private AgreementInfo current;

  private VerticalPanel radios;

  private Panel agreementGroup;
  private HTML agreementHtml;

  private Panel finalGroup;
  private NpTextBox yesIAgreeBox;
  private Button submit;

  public NewAgreementScreen() {
    this(null);
  }

  public NewAgreementScreen(String token) {
    nextToken = token != null ? token : PageLinks.SETTINGS_AGREEMENTS;
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    AccountApi.getAgreements(
        "self",
        new GerritCallback<JsArray<AgreementInfo>>() {
          @Override
          public void onSuccess(JsArray<AgreementInfo> result) {
            if (isAttached()) {
              mySigned = new HashSet<>();
              for (AgreementInfo info : Natives.asList(result)) {
                mySigned.add(info.name());
              }
              postRPC();
            }
          }
        });

    available = Gerrit.info().auth().contributorAgreements();
    postRPC();
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.C.newAgreement());

    final FlowPanel formBody = new FlowPanel();
    radios = new VerticalPanel();
    formBody.add(radios);

    agreementGroup = new FlowPanel();
    agreementGroup.add(new SmallHeading(Util.C.newAgreementReviewLegalHeading()));

    agreementHtml = new HTML();
    agreementHtml.setStyleName(Gerrit.RESOURCES.css().contributorAgreementLegal());
    agreementGroup.add(agreementHtml);
    formBody.add(agreementGroup);

    finalGroup = new VerticalPanel();
    finalGroup.add(new SmallHeading(Util.C.newAgreementCompleteHeading()));
    final FlowPanel fp = new FlowPanel();
    yesIAgreeBox = new NpTextBox();
    yesIAgreeBox.setVisibleLength(Util.C.newAgreementIAGREE().length() + 8);
    yesIAgreeBox.setMaxLength(Util.C.newAgreementIAGREE().length());
    fp.add(yesIAgreeBox);
    fp.add(new InlineLabel(Util.M.enterIAGREE(Util.C.newAgreementIAGREE())));
    finalGroup.add(fp);
    submit = new Button(Util.C.buttonSubmitNewAgreement());
    submit.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            doSign();
          }
        });
    finalGroup.add(submit);
    formBody.add(finalGroup);
    new OnEditEnabler(submit, yesIAgreeBox);

    final FormPanel form = new FormPanel();
    form.add(formBody);
    add(form);
  }

  private void postRPC() {
    if (mySigned != null && available != null) {
      renderSelf();
      display();
    }
  }

  private void renderSelf() {
    current = null;
    agreementGroup.setVisible(false);
    finalGroup.setVisible(false);
    radios.clear();

    final SmallHeading hdr = new SmallHeading();
    if (available.isEmpty()) {
      hdr.setText(Util.C.newAgreementNoneAvailable());
    } else {
      hdr.setText(Util.C.newAgreementSelectTypeHeading());
    }
    radios.add(hdr);

    for (AgreementInfo cla : available) {
      final RadioButton r = new RadioButton("cla_id", cla.name());
      r.addStyleName(Gerrit.RESOURCES.css().contributorAgreementButton());
      radios.add(r);

      if (mySigned.contains(cla.name())) {
        r.setEnabled(false);
        final Label l = new Label(Util.C.newAgreementAlreadySubmitted());
        l.setStyleName(Gerrit.RESOURCES.css().contributorAgreementAlreadySubmitted());
        radios.add(l);
      } else {
        r.addClickHandler(
            new ClickHandler() {
              @Override
              public void onClick(ClickEvent event) {
                showCLA(cla);
              }
            });
      }

      if (cla.description() != null && !cla.description().equals("")) {
        final Label l = new Label(cla.description());
        l.setStyleName(Gerrit.RESOURCES.css().contributorAgreementShortDescription());
        radios.add(l);
      }
    }
  }

  private void doSign() {
    submit.setEnabled(false);

    if (current == null || !Util.C.newAgreementIAGREE().equalsIgnoreCase(yesIAgreeBox.getText())) {
      yesIAgreeBox.setText("");
      yesIAgreeBox.setFocus(true);
      return;
    }
    doEnterAgreement();
  }

  private void doEnterAgreement() {
    AccountApi.enterAgreement(
        "self",
        current.name(),
        new GerritCallback<NativeString>() {
          @Override
          public void onSuccess(NativeString result) {
            Gerrit.display(nextToken);
          }

          @Override
          public void onFailure(Throwable caught) {
            yesIAgreeBox.setText("");
            super.onFailure(caught);
          }
        });
  }

  private void showCLA(AgreementInfo cla) {
    current = cla;
    String url = cla.url();
    if (url != null && url.length() > 0) {
      agreementGroup.setVisible(true);
      agreementHtml.setText(Gerrit.C.rpcStatusWorking());
      if (!url.startsWith("http:") && !url.startsWith("https:")) {
        url = GWT.getHostPageBaseURL() + url;
      }
      final RequestBuilder rb = new RequestBuilder(RequestBuilder.GET, url);
      rb.setCallback(
          new RequestCallback() {
            @Override
            public void onError(Request request, Throwable exception) {
              new ErrorDialog(exception).center();
            }

            @Override
            public void onResponseReceived(Request request, Response response) {
              final String ct = response.getHeader("Content-Type");
              if (response.getStatusCode() == 200
                  && ct != null
                  && (ct.equals("text/html") || ct.startsWith("text/html;"))) {
                agreementHtml.setHTML(response.getText());
              } else {
                new ErrorDialog(response.getStatusText()).center();
              }
            }
          });
      try {
        rb.send();
      } catch (RequestException e) {
        new ErrorDialog(e).show();
      }
    } else {
      agreementGroup.setVisible(false);
    }

    finalGroup.setVisible(cla.autoVerifyGroup() != null);
    yesIAgreeBox.setText("");
    submit.setEnabled(false);
  }
}
