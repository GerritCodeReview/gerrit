package com.google.gerrit.prettify.common;

import com.google.gwtorm.client.Column;

import java.util.List;

public class LineEdit extends BaseEdit {
  @Column(id = 5)
  protected List<BaseEdit> edits;

  public LineEdit(int beginA, int endA, int beginB, int endB,
      List<BaseEdit> edits) {
    this.beginA = beginA;
    this.endA = endA;
    this.beginB = beginB;
    this.endB = endB;
    this.edits = edits;
  }

  public LineEdit(BaseEdit edit, List<BaseEdit> edits) {
    this(edit.beginA, edit.endA, edit.beginB, edit.endB, edits);
  }

  public List<BaseEdit> getEdits() {
    return edits;
  }
}
