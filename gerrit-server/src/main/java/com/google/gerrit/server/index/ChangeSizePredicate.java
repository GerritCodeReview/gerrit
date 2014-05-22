package com.google.gerrit.server.index;

import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeData.ChangedLines;
import com.google.gwtorm.server.OrmException;

public abstract class ChangeSizePredicate extends IndexPredicate<ChangeData> {
  protected static enum Relation {
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    EQUAL,
  }

  private final Relation relation;
  private final int queryLines;

  protected ChangeSizePredicate(FieldDef<ChangeData, Integer> type,
      String value) throws QueryParseException {
    super(type, value);

    if (value.startsWith("<=")) {
      relation = Relation.LESS_THAN_OR_EQUAL;
      value = value.substring(2);
    } else if (value.startsWith("<")) {
      relation = Relation.LESS_THAN;
      value = value.substring(1);
    } else if (value.startsWith(">=")) {
      relation = Relation.GREATER_THAN_OR_EQUAL;
      value = value.substring(2);
    } else if (value.startsWith(">")) {
      relation = Relation.GREATER_THAN;
      value = value.substring(1);
    } else {
      relation = Relation.EQUAL;
    }

    try {
      queryLines = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new QueryParseException(e.getMessage(), e);
    }
  }

  protected abstract int getLines(ChangedLines cl);

  @Override
  public boolean match(ChangeData cd) throws OrmException {
    int lines = getLines(cd.changedLines());
    switch (relation) {
      case LESS_THAN:
        return queryLines < lines;
      case LESS_THAN_OR_EQUAL:
        return queryLines <= lines;
      case GREATER_THAN:
        return queryLines > lines;
      case GREATER_THAN_OR_EQUAL:
        return queryLines >= lines;
      case EQUAL:
        return queryLines == lines;
      default:
        throw new IllegalStateException("Unknown relation " + relation);
    }
  }

  @Override
  public int getCost() {
    return 1;
  }
}