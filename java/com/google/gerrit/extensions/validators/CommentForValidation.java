package com.google.gerrit.extensions.validators;

import com.google.auto.value.AutoValue;
import com.google.gerrit.extensions.validators.CommentValidationListener.CommentType;

@AutoValue
public abstract class CommentForValidation {
  public static CommentForValidation create(CommentType type, String text) {
    return new AutoValue_CommentForValidation(type, text);
  }

  public abstract CommentType getType();

  public abstract String getText();

  public CommentValidationFailure failValidation(String message) {
    return CommentValidationFailure.create(this, message);
  }
}
