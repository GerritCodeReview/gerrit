// Copyright 2009 Google Inc.
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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.ui.FancyFlexTable.MyFlexTable;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.HTMLTable;

public class FancyFlexTableImpl {
  public void resetHtml(final MyFlexTable myTable, final String body) {
    DOM.setInnerHTML(getBodyElement(myTable), body);
  }

  protected static native Element getBodyElement(HTMLTable myTable)
  /*-{ return myTable.@com.google.gwt.user.client.ui.HTMLTable::bodyElem; }-*/;
}
