package com.google.gerrit.server.events;

import com.google.common.base.Supplier;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.server.data.AccountAttribute;

public class DraftCommentAddedEvent extends ChangeEvent {
  static final String TYPE = "comment-added";
  public Supplier<AccountAttribute> author;

  @Nullable public String comment;

  public DraftCommentAddedEvent(Change change) {
    super(TYPE, change);
  }
}
