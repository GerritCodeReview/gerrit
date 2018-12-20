package com.google.gerrit.server.mail.send;

import com.google.common.base.CaseFormat;

public enum SoyTemplate {
  ABANDONED,
  ADD_KEY,
  CHANGE_FOOTER,
  CHANGE_SUBJECT(false),
  COMMENT,
  COMMENT_FOOTER,
  DELETE_REVIEWER,
  DELETE_VOTE,
  INBOUND_EMAIL_REJECTION,
  FOOTER,
  HEADER,
  MERGED,
  NEW_CHANGE,
  NO_REPLY_FOOTER,
  PRIVATE(false),
  REGISTER_NEW_EMAIL(false),
  REPLACE_PATCH_SET,
  RESTORED,
  REVERTED,
  SET_ASSIGNEE;

  private final boolean hasHtml;

  SoyTemplate() {
    this(true);
  }

  SoyTemplate(boolean hasHtml) {
    this.hasHtml = hasHtml;
  }

  public boolean hasHtml() {
    return hasHtml;
  }

  public String templateName() {
    return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, name());
  }

  public String htmlTemplateName() {
    if (!hasHtml) {
      throw new UnsupportedOperationException("template " + this + " has not HTML variant");
    }
    return templateName() + "Html";
  }
}
