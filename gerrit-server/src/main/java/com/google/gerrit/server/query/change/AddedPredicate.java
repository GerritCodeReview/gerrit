package com.google.gerrit.server.query.change;

import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.ChangeSizePredicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData.ChangedLines;

public class AddedPredicate extends ChangeSizePredicate {
  protected AddedPredicate(String value) throws QueryParseException {
    super(ChangeField.DELETED, value);
  }

  @Override
  protected int getLines(ChangedLines cl) {
    return cl.deletions;
  }
}
