package com.google.gerrit.client;

import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.user.client.PluginSafePopupPanel;

public class CurrentUserPopupPanel extends PluginSafePopupPanel {
  interface Binder extends UiBinder<Widget, CurrentUserPopupPanel> {
  }

  private static final Binder binder = GWT.create(Binder.class);
  public static final GerritConstants C = GWT.create(GerritConstants.class);

  @UiField
  Label userName;
  @UiField
  Label userEmail;
  @UiField
  Anchor settings;
  @UiField
  Anchor logout;

  public CurrentUserPopupPanel(Account account, boolean canLogOut) {
    super(/* auto hide */true, /* modal */false);
    setWidget(binder.createAndBindUi(this));
    if (account.getFullName() != null) {
      userName.setText(account.getFullName());
    }
    if (account.getPreferredEmail() != null) {
      userEmail.setText(account.getPreferredEmail());
    }
    settings.setHref(PageLinks.SETTINGS);
    if (!canLogOut) {
      logout.setVisible(false);
    } else {
      logout.setHref(Gerrit.selfRedirect("/logout"));
    }
  }
}
