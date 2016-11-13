// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.client.download;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.account.AccountApi;
import com.google.gerrit.client.info.DownloadInfo.DownloadSchemeInfo;
import com.google.gerrit.client.info.GeneralPreferences;
import com.google.gwt.aria.client.Roles;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Widget;
import java.util.ArrayList;
import java.util.List;

public class DownloadUrlLink extends Anchor implements ClickHandler {
  public static List<DownloadUrlLink> createDownloadUrlLinks(
      boolean allowAnonymous, DownloadPanel downloadPanel) {
    List<DownloadUrlLink> urls = new ArrayList<>();
    for (String s : Gerrit.info().download().schemes()) {
      DownloadSchemeInfo scheme = Gerrit.info().download().scheme(s);
      if (scheme.isAuthRequired() && !allowAnonymous) {
        continue;
      }
      urls.add(new DownloadUrlLink(downloadPanel, scheme, s));
    }
    return urls;
  }

  private final DownloadPanel downloadPanel;
  private final DownloadSchemeInfo schemeInfo;
  private final String schemeName;

  public DownloadUrlLink(
      DownloadPanel downloadPanel, DownloadSchemeInfo schemeInfo, String schemeName) {
    super(schemeName);
    setStyleName(Gerrit.RESOURCES.css().downloadLink());
    Roles.getTabRole().set(getElement());
    addClickHandler(this);

    this.downloadPanel = downloadPanel;
    this.schemeInfo = schemeInfo;
    this.schemeName = schemeName;
  }

  public String getSchemeName() {
    return schemeName;
  }

  @Override
  public void onClick(ClickEvent event) {
    event.preventDefault();
    event.stopPropagation();

    select();

    GeneralPreferences prefs = Gerrit.getUserPreferences();
    if (Gerrit.isSignedIn() && !schemeName.equals(prefs.downloadScheme())) {
      prefs.downloadScheme(schemeName);
      GeneralPreferences in = GeneralPreferences.create();
      in.downloadScheme(schemeName);
      AccountApi.self()
          .view("preferences")
          .put(
              in,
              new AsyncCallback<JavaScriptObject>() {
                @Override
                public void onSuccess(JavaScriptObject result) {}

                @Override
                public void onFailure(Throwable caught) {}
              });
    }
  }

  void select() {
    downloadPanel.populateDownloadCommandLinks(schemeInfo);

    DownloadUrlPanel parent = (DownloadUrlPanel) getParent();
    for (Widget w : parent) {
      if (w != this && w instanceof DownloadUrlLink) {
        w.removeStyleName(Gerrit.RESOURCES.css().downloadLink_Active());
      }
    }
    addStyleName(Gerrit.RESOURCES.css().downloadLink_Active());
  }
}
