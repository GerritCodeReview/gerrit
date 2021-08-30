package com.google.gerrit.server.query.change;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRecord.Status;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.rules.SubmitRule;
import java.util.Optional;

public class HasSubmitRulePredicate extends ChangeIndexPredicate {
  private final SubmitRule rule;

  public HasSubmitRulePredicate(Account.Id accountId, SubmitRule rule) {
    super(ChangeField.STARBY, accountId.toString());
    this.rule = rule;
  }

  @Override
  public boolean match(ChangeData cd) {
    Optional<SubmitRecord> record = rule.evaluate(cd);
    if (!record.isPresent()) {
      return true;
    }
    Status status = record.get().status;
    return status == Status.OK || status == Status.FORCED;
  }
}
