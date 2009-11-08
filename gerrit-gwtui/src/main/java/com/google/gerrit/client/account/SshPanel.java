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
import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.client.ui.TextSaveButtonListener;
import com.google.gerrit.common.data.SshHostKey;
import com.google.gerrit.common.errors.InvalidSshKeyException;
import com.google.gerrit.common.errors.InvalidSshUserNameException;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountSshKey;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwtexpui.globalkey.client.NpTextArea;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtjsonrpc.client.RemoteJsonException;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.HashSet;
import java.util.List;

class SshPanel extends Composite {
  private static boolean loadedApplet;
  private static Element applet;
  private static String appletErrorInvalidKey;
  private static String appletErrorSecurity;

  private int labelIdx, fieldIdx;

  private NpTextBox userNameTxt;
  private Button changeUserName;

  private SshKeyTable keys;

  private Button showAddKeyBlock;
  private Panel addKeyBlock;
  private Button closeAddKeyBlock;
  private Button clearNew;
  private Button addNew;
  private Button browse;
  private NpTextArea addTxt;
  private Button delSel;

  private Panel serverKeys;

  SshPanel() {
    if (LocaleInfo.getCurrentLocale().isRTL()) {
      labelIdx = 1;
      fieldIdx = 0;
    } else {
      labelIdx = 0;
      fieldIdx = 1;
    }

    final FlowPanel body = new FlowPanel();

    userNameTxt = new NpTextBox();
    if (Gerrit.isSignedIn()) {
      userNameTxt.setText(Gerrit.getUserAccount().getSshUserName());
    }
    userNameTxt.addKeyPressHandler(new SshUserNameValidator());
    userNameTxt.addStyleName("gerrit-SshPanel-username");
    userNameTxt.setVisibleLength(16);
    userNameTxt.setReadOnly(!canEditSshUserName());

    changeUserName = new Button(Util.C.buttonChangeSshUserName());
    changeUserName.setVisible(canEditSshUserName());
    changeUserName.setEnabled(false);
    changeUserName.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doChangeUserName();
      }
    });
    new TextSaveButtonListener(userNameTxt, changeUserName);

    final Grid userInfo = new Grid(1, 2);
    userInfo.setStyleName("gerrit-InfoBlock");
    userInfo.addStyleName("gerrit-AccountInfoBlock");
    body.add(userInfo);

    final FlowPanel userNameRow = new FlowPanel();
    userNameRow.add(userNameTxt);
    userNameRow.add(changeUserName);

    row(userInfo, 0, Util.C.sshUserName(), userNameRow);
    userInfo.getCellFormatter().addStyleName(0, 0, "topmost");
    userInfo.getCellFormatter().addStyleName(0, 0, "topmost");
    userInfo.getCellFormatter().addStyleName(0, 1, "topmost");
    userInfo.getCellFormatter().addStyleName(0, 0, "bottomheader");

    showAddKeyBlock = new Button(Util.C.buttonShowAddSshKey());
    showAddKeyBlock.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        showAddKeyBlock(true);
      }
    });

    keys = new SshKeyTable();
    body.add(keys);
    {
      final FlowPanel fp = new FlowPanel();
      delSel = new Button(Util.C.buttonDeleteSshKey());
      delSel.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          keys.deleteChecked();
        }
      });
      fp.add(delSel);
      fp.add(showAddKeyBlock);
      body.add(fp);
    }

    addKeyBlock = new VerticalPanel();
    addKeyBlock.setVisible(false);
    addKeyBlock.setStyleName("gerrit-AddSshKeyPanel");
    addKeyBlock.add(new SmallHeading(Util.C.addSshKeyPanelHeader()));
    addKeyBlock.add(new HTML(Util.C.addSshKeyHelp()));

    addTxt = new NpTextArea();
    addTxt.setVisibleLines(12);
    addTxt.setCharacterWidth(80);
    DOM.setElementPropertyBoolean(addTxt.getElement(), "spellcheck", false);
    addKeyBlock.add(addTxt);

    final HorizontalPanel buttons = new HorizontalPanel();
    addKeyBlock.add(buttons);

    clearNew = new Button(Util.C.buttonClearSshKeyInput());
    clearNew.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        addTxt.setText("");
        addTxt.setFocus(true);
      }
    });
    buttons.add(clearNew);

    browse = new Button(Util.C.buttonOpenSshKey());
    browse.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doBrowse();
      }
    });
    browse.setVisible(GWT.isScript() && (!loadedApplet || applet != null));
    buttons.add(browse);

    addNew = new Button(Util.C.buttonAddSshKey());
    addNew.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doAddNew();
      }
    });
    buttons.add(addNew);

    closeAddKeyBlock = new Button(Util.C.buttonCloseAddSshKey());
    closeAddKeyBlock.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        showAddKeyBlock(false);
      }
    });
    buttons.add(closeAddKeyBlock);
    buttons.setCellWidth(closeAddKeyBlock, "100%");
    buttons.setCellHorizontalAlignment(closeAddKeyBlock,
        HasHorizontalAlignment.ALIGN_RIGHT);

    body.add(addKeyBlock);

    serverKeys = new FlowPanel();
    body.add(serverKeys);

    initWidget(body);
  }

  private boolean canEditSshUserName() {
    return Gerrit.getConfig().canEdit(Account.FieldName.SSH_USER_NAME);
  }

  protected void row(final Grid info, final int row, final String name,
      final Widget field) {
    info.setText(row, labelIdx, name);
    info.setWidget(row, fieldIdx, field);
    info.getCellFormatter().addStyleName(row, 0, "header");
  }

  void setKeyTableVisible(final boolean on) {
    keys.setVisible(on);
    delSel.setVisible(on);
    closeAddKeyBlock.setVisible(on);
  }

  void doChangeUserName() {
    if (!canEditSshUserName()) {
      return;
    }

    String newName = userNameTxt.getText();
    if ("".equals(newName)) {
      newName = null;
    }
    if (newName != null && !newName.matches(Account.SSH_USER_NAME_PATTERN)) {
      invalidUserName();
      return;
    }

    userNameTxt.setEnabled(false);
    changeUserName.setEnabled(false);

    final String newSshUserName = newName;
    Util.ACCOUNT_SEC.changeSshUserName(newSshUserName,
        new GerritCallback<VoidResult>() {
          public void onSuccess(final VoidResult result) {
            userNameTxt.setEnabled(true);
            changeUserName.setEnabled(false);
            if (Gerrit.isSignedIn()) {
              Gerrit.getUserAccount().setSshUserName(newSshUserName);
            }
          }

          @Override
          public void onFailure(final Throwable caught) {
            userNameTxt.setEnabled(true);
            changeUserName.setEnabled(true);
            if (InvalidSshUserNameException.MESSAGE.equals(caught.getMessage())) {
              invalidUserName();
            } else {
              super.onFailure(caught);
            }
          }
        });
  }

  void invalidUserName() {
    userNameTxt.setFocus(true);
    new ErrorDialog(Util.C.invalidSshUserName()).center();
  }

  void doBrowse() {
    browse.setEnabled(false);
    if (!loadedApplet) {
      applet = DOM.createElement("applet");
      applet.setAttribute("code",
          "com.google.gerrit.keyapplet.ReadPublicKey.class");
      applet.setAttribute("archive", GWT.getModuleBaseURL()
          + "gerrit-keyapplet.cache.jar?v=" + Gerrit.getVersion());
      applet.setAttribute("mayscript", "true");
      applet.setAttribute("width", "0");
      applet.setAttribute("height", "0");
      RootPanel.getBodyElement().appendChild(applet);
      loadedApplet = true;

      // We have to defer to allow the event loop time to setup that
      // new applet tag we just created above, and actually load the
      // applet into the runtime.
      //
      DeferredCommand.addCommand(new Command() {
        public void execute() {
          doBrowse();
        }
      });
      return;
    }
    if (applet == null) {
      // If the applet element is null, the applet was determined
      // to have failed to load, and we are dead. Hide the button.
      //
      noBrowse();
      return;
    }

    String txt;
    try {
      txt = openPublicKey(applet);
    } catch (RuntimeException re) {
      // If this call fails, the applet is dead. It is most likely
      // not loading due to Java support being disabled.
      //
      noBrowse();
      return;
    }
    if (txt == null) {
      txt = "";
    }

    browse.setEnabled(true);

    if (appletErrorInvalidKey == null) {
      appletErrorInvalidKey = getErrorInvalidKey(applet);
      appletErrorSecurity = getErrorSecurity(applet);
    }

    if (appletErrorInvalidKey.equals(txt)) {
      new ErrorDialog(Util.C.invalidSshKeyError()).center();
      return;
    }
    if (appletErrorSecurity.equals(txt)) {
      new ErrorDialog(Util.C.invalidSshKeyError()).center();
      return;
    }

    addTxt.setText(txt);
    addNew.setFocus(true);
  }

  private void noBrowse() {
    if (applet != null) {
      applet.getParentElement().removeChild(applet);
      applet = null;
    }
    browse.setVisible(false);
    new ErrorDialog(Util.C.sshJavaAppletNotAvailable()).center();
  }

  private static native String openPublicKey(Element keyapp)
  /*-{ var r = keyapp.openPublicKey(); return r == null ? null : ''+r; }-*/;

  private static native String getErrorInvalidKey(Element keyapp)
  /*-{ return ''+keyapp.getErrorInvalidKey(); }-*/;

  private static native String getErrorSecurity(Element keyapp)
  /*-{ return ''+keyapp.getErrorSecurity(); }-*/;

  void doAddNew() {
    final String txt = addTxt.getText();
    if (txt != null && txt.length() > 0) {
      addNew.setEnabled(false);
      Util.ACCOUNT_SEC.addSshKey(txt, new GerritCallback<AccountSshKey>() {
        public void onSuccess(final AccountSshKey result) {
          addNew.setEnabled(true);
          addTxt.setText("");
          keys.addOneKey(result);
          if (!keys.isVisible()) {
            showAddKeyBlock(false);
            setKeyTableVisible(true);
          }
        }

        @Override
        public void onFailure(final Throwable caught) {
          addNew.setEnabled(true);

          if (isInvalidSshKey(caught)) {
            new ErrorDialog(Util.C.invalidSshKeyError()).center();

          } else {
            super.onFailure(caught);
          }
        }

        private boolean isInvalidSshKey(final Throwable caught) {
          if (caught instanceof InvalidSshKeyException) {
            return true;
          }
          return caught instanceof RemoteJsonException
              && InvalidSshKeyException.MESSAGE.equals(caught.getMessage());
        }
      });
    }
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    userNameTxt.setEnabled(false);
    Util.ACCOUNT_SVC.myAccount(new GerritCallback<Account>() {
      public void onSuccess(final Account result) {
        if (Gerrit.isSignedIn()) {
          Gerrit.getUserAccount().setSshUserName(result.getSshUserName());
        }
        userNameTxt.setText(result.getSshUserName());
        userNameTxt.setEnabled(true);
      }
    });

    Util.ACCOUNT_SEC.mySshKeys(new GerritCallback<List<AccountSshKey>>() {
      public void onSuccess(final List<AccountSshKey> result) {
        keys.display(result);
        if (result.isEmpty() && keys.isVisible()) {
          showAddKeyBlock(true);
        }
      }
    });

    Gerrit.SYSTEM_SVC.daemonHostKeys(new GerritCallback<List<SshHostKey>>() {
      public void onSuccess(final List<SshHostKey> result) {
        serverKeys.clear();
        for (final SshHostKey keyInfo : result) {
          serverKeys.add(new SshHostKeyPanel(keyInfo));
        }
      }
    });
  }

  private void showAddKeyBlock(final boolean show) {
    showAddKeyBlock.setVisible(!show);
    addKeyBlock.setVisible(show);
  }

  private final class SshUserNameValidator implements KeyPressHandler {
    @Override
    public void onKeyPress(final KeyPressEvent event) {
      final char code = event.getCharCode();
      switch (code) {
        case KeyCodes.KEY_ALT:
        case KeyCodes.KEY_BACKSPACE:
        case KeyCodes.KEY_CTRL:
        case KeyCodes.KEY_DELETE:
        case KeyCodes.KEY_DOWN:
        case KeyCodes.KEY_END:
        case KeyCodes.KEY_ENTER:
        case KeyCodes.KEY_ESCAPE:
        case KeyCodes.KEY_HOME:
        case KeyCodes.KEY_LEFT:
        case KeyCodes.KEY_PAGEDOWN:
        case KeyCodes.KEY_PAGEUP:
        case KeyCodes.KEY_RIGHT:
        case KeyCodes.KEY_SHIFT:
        case KeyCodes.KEY_TAB:
        case KeyCodes.KEY_UP:
          // Allow these, even if one of their assigned codes is
          // identical to an ASCII character we do not want to
          // allow in the box.
          //
          // We still want to let the user move around the input box
          // with their arrow keys, or to move between fields using tab.
          // Invalid characters introduced will be caught through the
          // server's own validation of the input data.
          //
          break;

        default:
          final TextBox box = (TextBox) event.getSource();
          final String re;
          if (box.getCursorPos() == 0)
            re = Account.SSH_USER_NAME_PATTERN_FIRST;
          else
            re = Account.SSH_USER_NAME_PATTERN_REST;
          if (!String.valueOf(code).matches("^" + re + "$")) {
            event.preventDefault();
            event.stopPropagation();
          }
      }
    }
  }

  private class SshKeyTable extends FancyFlexTable<AccountSshKey> {
    private static final String S_INVALID = "gerrit-SshKeyPanel-Invalid";

    SshKeyTable() {
      table.setWidth("");
      table.setText(0, 3, Util.C.sshKeyAlgorithm());
      table.setText(0, 4, Util.C.sshKeyKey());
      table.setText(0, 5, Util.C.sshKeyComment());
      table.setText(0, 6, Util.C.sshKeyLastUsed());
      table.setText(0, 7, Util.C.sshKeyStored());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, S_ICON_HEADER);
      fmt.addStyleName(0, 2, S_DATA_HEADER);
      fmt.addStyleName(0, 3, S_DATA_HEADER);
      fmt.addStyleName(0, 4, S_DATA_HEADER);
      fmt.addStyleName(0, 5, S_DATA_HEADER);
      fmt.addStyleName(0, 6, S_DATA_HEADER);
      fmt.addStyleName(0, 7, S_DATA_HEADER);
    }

    void deleteChecked() {
      final HashSet<AccountSshKey.Id> ids = new HashSet<AccountSshKey.Id>();
      for (int row = 1; row < table.getRowCount(); row++) {
        final AccountSshKey k = getRowItem(row);
        if (k != null && ((CheckBox) table.getWidget(row, 1)).getValue()) {
          ids.add(k.getKey());
        }
      }
      if (!ids.isEmpty()) {
        Util.ACCOUNT_SEC.deleteSshKeys(ids, new GerritCallback<VoidResult>() {
          public void onSuccess(final VoidResult result) {
            for (int row = 1; row < table.getRowCount();) {
              final AccountSshKey k = getRowItem(row);
              if (k != null && ids.contains(k.getKey())) {
                table.removeRow(row);
              } else {
                row++;
              }
            }
            if (table.getRowCount() == 1) {
              showAddKeyBlock(true);
            }
          }
        });
      }
    }

    void display(final List<AccountSshKey> result) {
      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final AccountSshKey k : result) {
        addOneKey(k);
      }
    }

    void addOneKey(final AccountSshKey k) {
      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      final int row = table.getRowCount();
      table.insertRow(row);
      applyDataRowStyle(row);

      table.setWidget(row, 1, new CheckBox());
      if (k.isValid()) {
        table.setText(row, 2, "");
        fmt.removeStyleName(row, 2, S_INVALID);
      } else {
        table.setText(row, 2, Util.C.sshKeyInvalid());
        fmt.addStyleName(row, 2, S_INVALID);
      }
      table.setText(row, 3, k.getAlgorithm());
      table.setText(row, 4, elide(k.getEncodedKey()));
      table.setText(row, 5, k.getComment());
      table.setText(row, 6, FormatUtil.mediumFormat(k.getLastUsedOn()));
      table.setText(row, 7, FormatUtil.mediumFormat(k.getStoredOn()));

      fmt.addStyleName(row, 1, S_ICON_CELL);
      fmt.addStyleName(row, 2, S_ICON_CELL);
      fmt.addStyleName(row, 4, "gerrit-SshKeyPanel-EncodedKey");
      for (int c = 3; c <= 7; c++) {
        fmt.addStyleName(row, c, S_DATA_CELL);
      }
      fmt.addStyleName(row, 6, "C_LAST_UPDATE");
      fmt.addStyleName(row, 7, "C_LAST_UPDATE");

      setRowItem(row, k);
    }

    String elide(final String s) {
      if (s == null || s.length() < 40) {
        return s;
      }
      return s.substring(0, 30) + "..." + s.substring(s.length() - 10);
    }
  }
}
