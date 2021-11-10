package com.google.gerrit.server.query.change;

import com.google.inject.Singleton;

/**
 * A submit requirement predicate (can only be used in submit requirement expressions) that always
 * evaluates to {@code true} if the value is equal to "true" or false otherwise.
 */
@Singleton
public class ConstantPredicate extends SubmitRequirementPredicate {
  public ConstantPredicate(String value) {
    super("is", value);
  }

  @Override
  public boolean match(ChangeData object) {
    return "true".equals(value);
  }

  @Override
  public int getCost() {
    return 1;
  }
}
