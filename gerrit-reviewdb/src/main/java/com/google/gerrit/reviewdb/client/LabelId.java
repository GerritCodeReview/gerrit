package com.google.gerrit.reviewdb.client;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.StringKey;

public class LabelId extends StringKey<com.google.gwtorm.client.Key<?>> {
  private static final long serialVersionUID = 1L;

  public static final LabelId SUBMIT = new LabelId("SUBM");

  @Column(id = 1)
  protected String id;

  protected LabelId() {
  }

  public LabelId(final String n) {
    id = n;
  }

  @Override
  public String get() {
    return id;
  }

  @Override
  protected void set(String newValue) {
    id = newValue;
  }

  @Override
  public int hashCode() {
    return get().hashCode();
  }

  @Override
  public boolean equals(Object b) {
    if (b instanceof LabelId) {
      return get().equals(((LabelId) b).get());
    }
    return false;
  }
}
