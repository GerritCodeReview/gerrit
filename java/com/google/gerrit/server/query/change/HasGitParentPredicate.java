package com.google.gerrit.server.query.change;

import com.google.gerrit.index.query.Matchable;
import com.google.gerrit.index.query.OperatorPredicate;
import com.google.gerrit.server.git.GitRepositoryManager;

public class HasGitParentPredicate extends OperatorPredicate<ChangeData>
    implements Matchable<ChangeData> {
  protected final GitRepositoryManager repoManager;

  public HasGitParentPredicate(String value, GitRepositoryManager repoManager) {
    super(ChangeQueryBuilder.FIELD_GITPARENT, value);
    this.repoManager = repoManager;
  }

  @Override
  public boolean match(ChangeData change) {
    return !ParentOfPredicate.getParents(change, repoManager).isEmpty();
  }

  @Override
  public int getCost() {
    return 1;
  }
}
