package com.google.gerrit.client.editor;

import com.google.gerrit.client.DiffWebLinkInfo;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;

public class ChangeEditInfo extends JavaScriptObject {
  public final native JsArray<DiffWebLinkInfo> web_links() /*-{ return this.web_links; }-*/;

  protected ChangeEditInfo() {
  }
}
