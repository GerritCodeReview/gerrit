package com.google.gerrit.server.query.change;

import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.ChangeSizePredicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData.ChangedLines;

public class DeltaPredicate extends ChangeSizePredicate {
  protected DeltaPredicate(String value) throws QueryParseException {
    super(ChangeField.DELTA, value);
  }

  @Override
  protected int getLines(ChangedLines cl) {
    return cl.insertions + cl.deletions;
  }
}
