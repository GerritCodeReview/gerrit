package com.google.gerrit.server.query.change;

import com.google.inject.Singleton;

/**
 * A submit requirement predicate (can only be used in submit requirement expressions) that always
 * evaluates to false.
 *
 * <p>Example usage can include redefining a submit requirement in child projects and skip them by
 * setting the applicability expression to "always:false".
 */
@Singleton
public class FalsePredicate extends SubmitRequirementPredicate {
  public FalsePredicate() {
    super("is", "false");
  }

  @Override
  public boolean match(ChangeData object) {
    return false;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
