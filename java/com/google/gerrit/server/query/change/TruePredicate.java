package com.google.gerrit.server.query.change;

import com.google.inject.Singleton;

/**
 * A submit requirement predicate (can only be used in submit requirement expressions) that always
 * evaluates to true.
 */
@Singleton
public class TruePredicate extends SubmitRequirementPredicate {
  public TruePredicate() {
    super("is", "true");
  }

  @Override
  public boolean match(ChangeData object) {
    return true;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
