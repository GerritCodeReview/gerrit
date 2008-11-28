package com.google.gerrit.client;

import com.google.gerrit.client.ui.Screen;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;

/** Displays an error message letting the user know the page doesn't exist. */
public class NotFoundScreen extends Screen {
  public NotFoundScreen() {
    super(Gerrit.C.notFoundTitle());

    final Element body = DOM.createDiv();
    DOM.setInnerText(body, Gerrit.C.notFoundBody());
    DOM.appendChild(getElement(), body);
  }
}
