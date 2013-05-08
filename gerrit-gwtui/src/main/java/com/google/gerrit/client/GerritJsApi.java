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
  public static class NewRow {
    public final int position;
    public final String title;
    public final Widget widget;

    public NewRow(int position, String title, Widget widget) {
      this.position = position;
      this.title = title;
      this.widget = widget;
    }
  }

  public static List<NewRow> informChangeInfoRowModifiers(int changeNo) {
    List<NewRow> rows = new ArrayList<NewRow>();
    for (ChangeInfoRowModifier modifier : changeInfoRowAdders) {
      Element widget = callInfoRowModifierCallback(modifier.callback, changeNo);
      rows.add(new NewRow(modifier.position, modifier.title, InlineLabel.wrap(widget)));
    }
    return rows;
  }

  public static native void expose() /*-{
    $wnd['gerrit'] = {};
    $wnd['gerrit']['onChangeScreen'] = function() {
      return {'addChangeInfoRow': function(position, title, callback) {
        @com.google.gerrit.client.GerritJsApi::addChangeInfoRow(ILjava/lang/String;Lcom/google/gwt/core/client/JavaScriptObject;)(position, title, callback);
      }};
    }
  }-*/;

  private static final List<ChangeInfoRowModifier> changeInfoRowAdders = new ArrayList<ChangeInfoRowModifier>();

  private static void addChangeInfoRow(int position, String title, JavaScriptObject callback) {
    changeInfoRowAdders.add(new ChangeInfoRowModifier(position, title, callback));
  }

  private static native Element callInfoRowModifierCallback(JavaScriptObject callback, int changeNo) /*-{
    return callback(changeNo);
  }-*/;

  private static class ChangeInfoRowModifier {
    final int position;
    final String title;
    final JavaScriptObject callback;

    ChangeInfoRowModifier(int position, String title, JavaScriptObject callback) {
      this.title = title;
      this.position = position;
      this.callback = callback;
    }
  }
}
