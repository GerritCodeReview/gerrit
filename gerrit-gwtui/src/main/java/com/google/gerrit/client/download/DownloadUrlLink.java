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
import com.google.gerrit.client.config.DownloadInfo.DownloadSchemeInfo;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadScheme;
import com.google.gwt.aria.client.Roles;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.VoidResult;

import java.util.ArrayList;
import java.util.List;

public class DownloadUrlLink extends Anchor implements ClickHandler {
  private enum KnownScheme {
    ANON_GIT(DownloadScheme.ANON_GIT, "git", Util.M.anonymousDownload("Git")),
    ANON_HTTP(DownloadScheme.ANON_HTTP, "anonymous http", Util.M.anonymousDownload("HTTP")),
    SSH(DownloadScheme.SSH, "ssh", "SSH"),
    HTTP(DownloadScheme.HTTP, "http", "HTTP");

    public final DownloadScheme downloadScheme;
    public final String name;
    public final String text;

    private KnownScheme(DownloadScheme downloadScheme, String name, String text) {
      this.downloadScheme = downloadScheme;
      this.name = name;
      this.text = text;
    }

    static KnownScheme get(String name) {
      for (KnownScheme s : values()) {
        if (s.name.equals(name)) {
          return s;
        }
      }
      return null;
    }
  }

  public static List<DownloadUrlLink> createDownloadUrlLinks(
      boolean allowAnonymous, DownloadPanel downloadPanel) {
    List<DownloadUrlLink> urls = new ArrayList<>();
    for (String s : Gerrit.info().download().schemes()) {
      DownloadSchemeInfo scheme = Gerrit.info().download().scheme(s);
      if (scheme.isAuthRequired() && !allowAnonymous) {
        continue;
      }

      KnownScheme knownScheme = KnownScheme.get(s);
      if (knownScheme != null) {
        urls.add(new DownloadUrlLink(downloadPanel, scheme,
            knownScheme.downloadScheme, knownScheme.text));
      } else {
        urls.add(new DownloadUrlLink(downloadPanel, scheme, s));
      }
    }
    return urls;
  }

  private final DownloadPanel downloadPanel;
  private final DownloadSchemeInfo schemeInfo;
  private final DownloadScheme urlType;

  public DownloadUrlLink(DownloadPanel downloadPanel,
      DownloadSchemeInfo schemeInfo, String text) {
    this(downloadPanel, schemeInfo, null, text);
  }

  public DownloadUrlLink(DownloadPanel downloadPanel,
      DownloadSchemeInfo schemeInfo, DownloadScheme urlType, String text) {
    super(text);
    setStyleName(Gerrit.RESOURCES.css().downloadLink());
    Roles.getTabRole().set(getElement());
    addClickHandler(this);

    this.downloadPanel = downloadPanel;
    this.schemeInfo = schemeInfo;
    this.urlType = urlType;
  }

  public DownloadScheme getUrlType() {
    return urlType;
  }

  @Override
  public void onClick(ClickEvent event) {
    event.preventDefault();
    event.stopPropagation();

    select();

    if (Gerrit.isSignedIn() && urlType != null) {
      // If the user is signed-in, remember this choice for future panels.
      //
      AccountGeneralPreferences pref =
          Gerrit.getUserAccount().getGeneralPreferences();
      pref.setDownloadUrl(urlType);
      com.google.gerrit.client.account.Util.ACCOUNT_SVC.changePreferences(pref,
          new AsyncCallback<VoidResult>() {
            @Override
            public void onFailure(Throwable caught) {
            }

            @Override
            public void onSuccess(VoidResult result) {
            }
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
