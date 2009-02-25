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

public class FancyFlexTableImplIE6 extends FancyFlexTableImpl {
  @Override
  public void resetHtml(final MyFlexTable myTable, final String bodyHtml) {
    final Element oldBody = getBodyElement(myTable);
    final Element newBody = parseBody(bodyHtml);
    assert newBody != null;

    final Element tableElem = DOM.getParent(oldBody);
    DOM.removeChild(tableElem, oldBody);
    setBodyElement(myTable, newBody);
    DOM.appendChild(tableElem, newBody);
  }

  private static Element parseBody(final String body) {
    final Element div = DOM.createDiv();
    DOM.setInnerHTML(div, "<table>" + body + "</table>");

    final Element newTable = DOM.getChild(div, 0);
    for (Element e = DOM.getFirstChild(newTable); e != null; e =
        DOM.getNextSibling(e)) {
      if ("tbody".equals(e.getTagName().toLowerCase())) {
        return e;
      }
    }
    return null;
  }

  private static native void setBodyElement(HTMLTable myTable, Element newBody)
  /*-{ myTable.@com.google.gwt.user.client.ui.HTMLTable::bodyElem = newBody; }-*/;
}
