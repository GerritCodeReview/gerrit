package com.google.gerrit.client.diff;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.TableElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.UIObject;

/**
 * A diff table.
 */
public class DiffTable extends UIObject {
  interface MyUiBinder extends UiBinder<TableElement, DiffTable> {}
  private static MyUiBinder uiBinder = GWT.create(MyUiBinder.class);

  @UiField DivElement cmA;
  @UiField DivElement cmB;

  public DiffTable() {
    setElement(uiBinder.createAndBindUi(this));
  }

  public DivElement getCmA() {
    return cmA;
  }

  public DivElement getCmB() {
    return cmB;
  }

}
