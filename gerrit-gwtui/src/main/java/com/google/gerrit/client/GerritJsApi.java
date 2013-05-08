// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;

public class GerritJsApi {
  public static class NewChangeInfoTableRow {
    public final int position;
    public final String title;
    public final Widget widget;

    public NewChangeInfoTableRow(int position, String title, Widget widget) {
      this.title = title;
      this.widget = widget;
      this.position = position;
    }
  }

  public static List<NewChangeInfoTableRow> informChangeInfoTableModifiers(int changeNo) {
    List<NewChangeInfoTableRow> rows = new ArrayList<NewChangeInfoTableRow>();
    for (ChangeInfoTableModifier modifier : changeInfoRowAdders) {
      try {
        Element widget = callInfoRowModifierCallback(modifier.callback, changeNo);
        InlineLabel wrapped = InlineLabel.wrap(widget);
        rows.add(new NewChangeInfoTableRow(modifier.position, modifier.title, wrapped));
      } catch (Throwable t) {
        consoleLog(t.getMessage());
      }
    }
    return rows;
  }

  public static native void expose() /*-{
    var insertRowToChangeInfoTable = function(position, title, callback) {
        @com.google.gerrit.client.GerritJsApi::insertRowToChangeInfoTable(ILjava/lang/String;Lcom/google/gwt/core/client/JavaScriptObject;)(position, title, callback);
      };

    $wnd['gerrit'] = {
        'onChangeScreen': {
            'insertRowToChangeInfoTable': insertRowToChangeInfoTable}};
  }-*/;

  private static final List<ChangeInfoTableModifier> changeInfoRowAdders = new ArrayList<ChangeInfoTableModifier>();

  private static void insertRowToChangeInfoTable(int position, String title, JavaScriptObject callback) {
    changeInfoRowAdders.add(new ChangeInfoTableModifier(position, title, callback));
  }

  private static native Element callInfoRowModifierCallback(
      JavaScriptObject callback, int changeNo) /*-{
    return callback(changeNo);
  }-*/;

  private static native void consoleLog(String msg) /*-{
    console.log(msg);
  }-*/;

  private static class ChangeInfoTableModifier {
    final int position;
    final String title;
    final JavaScriptObject callback;

    ChangeInfoTableModifier(int position, String title, JavaScriptObject callback) {
      this.title = title;
      this.position = position;
      this.callback = callback;
    }
  }
}
