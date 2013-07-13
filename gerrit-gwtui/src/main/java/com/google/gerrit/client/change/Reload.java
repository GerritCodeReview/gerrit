package com.google.gerrit.client.change;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.user.client.ui.Image;

class Reload extends Image implements ClickHandler,
    MouseOverHandler, MouseOutHandler {
  private Change.Id changeId;
  private boolean in;

  Reload() {
    setResource(Resources.I.reload_black());
    addClickHandler(this);
    addMouseOverHandler(this);
    addMouseOutHandler(this);
  }

  void set(ChangeInfo info) {
    changeId = info.legacy_id();
  }

  void reload() {
    Gerrit.display(PageLinks.toChange2(changeId));
  }

  @Override
  public void onMouseOver(MouseOverEvent event) {
    if (!in) {
      in = true;
      setResource(Resources.I.reload_white());
    }
  }

  @Override
  public void onMouseOut(MouseOutEvent event) {
    if (in) {
      in = false;
      setResource(Resources.I.reload_black());
    }
  }

  @Override
  public void onClick(ClickEvent e) {
    e.preventDefault();
    e.stopPropagation();
  }
}
