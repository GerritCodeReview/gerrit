package com.google.gerrit.server.query.change;

import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.ChangeSizePredicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData.ChangedLines;

public class DeletedPredicate extends ChangeSizePredicate {
  protected DeletedPredicate(String value) throws QueryParseException {
    super(ChangeField.ADDED, value);
  }

  @Override
  protected int getLines(ChangedLines cl) {
    return cl.insertions;
  }
}
