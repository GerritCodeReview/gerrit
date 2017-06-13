// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.info.GeneralPreferences;
import com.google.gerrit.client.info.OAuthTokenInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.clippy.client.CopyableLabel;
import java.util.Date;

public class MyOAuthTokenScreen extends SettingsScreen {
  private CopyableLabel tokenLabel;
  private Label expiresLabel;
  private Label expiredNote;
  private CopyableLabel netrcValue;
  private CopyableLabel cookieValue;
  private FlowPanel flow;
  private Grid grid;

  @Override
  protected void onInitUI() {
    super.onInitUI();

    tokenLabel = new CopyableLabel("");
    tokenLabel.addStyleName(Gerrit.RESOURCES.css().oauthToken());

    expiresLabel = new Label("");
    expiresLabel.addStyleName(Gerrit.RESOURCES.css().oauthExpires());

    grid = new Grid(2, 2);
    grid.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    grid.addStyleName(Gerrit.RESOURCES.css().oauthInfoBlock());
    add(grid);

    expiredNote = new Label(Util.C.labelOAuthExpired());
    expiredNote.setVisible(false);
    add(expiredNote);

    row(grid, 0, Util.C.labelOAuthToken(), tokenLabel);
    row(grid, 1, Util.C.labelOAuthExpires(), expiresLabel);

    CellFormatter fmt = grid.getCellFormatter();
    fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(1, 0, Gerrit.RESOURCES.css().bottomheader());

    flow = new FlowPanel();
    flow.setStyleName(Gerrit.RESOURCES.css().oauthPanel());
    add(flow);

    Label netrcLabel = new Label(Util.C.labelOAuthNetRCEntry());
    netrcLabel.setStyleName(Gerrit.RESOURCES.css().oauthPanelNetRCHeading());
    flow.add(netrcLabel);
    netrcValue = new CopyableLabel("");
    netrcValue.setStyleName(Gerrit.RESOURCES.css().oauthPanelNetRCEntry());
    flow.add(netrcValue);

    Label cookieLabel = new Label(Util.C.labelOAuthGitCookie());
    cookieLabel.setStyleName(Gerrit.RESOURCES.css().oauthPanelCookieHeading());
    flow.add(cookieLabel);
    cookieValue = new CopyableLabel("");
    cookieValue.setStyleName(Gerrit.RESOURCES.css().oauthPanelCookieEntry());
    flow.add(cookieValue);
  }

  private void row(Grid grid, int row, String name, Widget field) {
    final CellFormatter fmt = grid.getCellFormatter();
    if (LocaleInfo.getCurrentLocale().isRTL()) {
      grid.setText(row, 1, name);
      grid.setWidget(row, 0, field);
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().header());
    } else {
      grid.setText(row, 0, name);
      grid.setWidget(row, 1, field);
      fmt.addStyleName(row, 0, Gerrit.RESOURCES.css().header());
    }
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    AccountApi.self()
        .view("preferences")
        .get(
            new ScreenLoadCallback<GeneralPreferences>(this) {
              @Override
              protected void preDisplay(GeneralPreferences prefs) {
                display(prefs);
              }
            });
  }

  private void display(GeneralPreferences prefs) {
    AccountApi.self()
        .view("oauthtoken")
        .get(
            new GerritCallback<OAuthTokenInfo>() {
              @Override
              public void onSuccess(OAuthTokenInfo tokenInfo) {
                tokenLabel.setText(tokenInfo.accessToken());
                expiresLabel.setText(getExpiresAt(tokenInfo, prefs));
                netrcValue.setText(getNetRC(tokenInfo));
                cookieValue.setText(getCookie(tokenInfo));
                flow.setVisible(true);
                expiredNote.setVisible(false);
              }

              @Override
              public void onFailure(Throwable caught) {
                if (isNoSuchEntity(caught) || isSigninFailure(caught)) {
                  tokenLabel.setText("");
                  expiresLabel.setText("");
                  netrcValue.setText("");
                  cookieValue.setText("");
                  flow.setVisible(false);
                  expiredNote.setVisible(true);
                } else {
                  showFailure(caught);
                }
              }
            });
  }

  private static long getExpiresAt(OAuthTokenInfo tokenInfo) {
    if (tokenInfo.expiresAt() == null) {
      return Long.MAX_VALUE;
    }
    long expiresAt;
    try {
      expiresAt = Long.parseLong(tokenInfo.expiresAt());
    } catch (NumberFormatException e) {
      return Long.MAX_VALUE;
    }
    return expiresAt;
  }

  private static long getExpiresAtSeconds(OAuthTokenInfo tokenInfo) {
    return getExpiresAt(tokenInfo) / 1000L;
  }

  private static String getExpiresAt(OAuthTokenInfo tokenInfo, GeneralPreferences prefs) {
    long expiresAt = getExpiresAt(tokenInfo);
    if (expiresAt == Long.MAX_VALUE) {
      return "";
    }
    String dateFormat = prefs.dateFormat().getLongFormat();
    String timeFormat = prefs.timeFormat().getFormat();
    DateTimeFormat formatter = DateTimeFormat.getFormat(dateFormat + " " + timeFormat);
    return formatter.format(new Date(expiresAt));
  }

  private static String getNetRC(OAuthTokenInfo accessTokenInfo) {
    StringBuilder sb = new StringBuilder();
    sb.append("machine ");
    sb.append(accessTokenInfo.resourceHost());
    sb.append(" login ");
    sb.append(accessTokenInfo.username());
    sb.append(" password ");
    sb.append(accessTokenInfo.accessToken());
    return sb.toString();
  }

  private static String getCookie(OAuthTokenInfo accessTokenInfo) {
    StringBuilder sb = new StringBuilder();
    sb.append(accessTokenInfo.resourceHost());
    sb.append("\tFALSE\t/\tTRUE\t");
    sb.append(getExpiresAtSeconds(accessTokenInfo));
    sb.append("\tgit-");
    sb.append(accessTokenInfo.username());
    sb.append('\t');
    sb.append(accessTokenInfo.accessToken());
    if (accessTokenInfo.providerId() != null) {
      sb.append('@').append(accessTokenInfo.providerId());
    }
    return sb.toString();
  }
}
