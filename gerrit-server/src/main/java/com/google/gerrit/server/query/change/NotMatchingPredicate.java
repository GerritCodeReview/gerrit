package com.google.gerrit.server.query.change;

import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.client.OrmException;

public class NotMatchingPredicate<T> extends OperatorPredicate<T> {

  public NotMatchingPredicate(final String name, final String value) {
    super(name, value);
  }

  @Override
  public boolean match(T object) throws OrmException {
    return false;
  }

  @Override
  public int getCost() {
    return 0;
  }

}
