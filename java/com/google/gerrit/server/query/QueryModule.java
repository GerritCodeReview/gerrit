package com.google.gerrit.server.query;

import com.google.gerrit.server.AccountPredicateParser;
import com.google.gerrit.server.ChangePredicateParser;
import com.google.gerrit.server.GroupPredicateParser;
import com.google.gerrit.server.ProjectPredicateParser;
import com.google.gerrit.server.query.account.AccountQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.group.GroupQueryBuilder;
import com.google.gerrit.server.query.project.ProjectQueryBuilder;
import com.google.inject.AbstractModule;

public class QueryModule extends AbstractModule {
  @Override
  public void configure() {
    bind(ChangePredicateParser.class).to(ChangeQueryBuilder.class);
    bind(ProjectPredicateParser.class).to(ProjectQueryBuilder.class);
    bind(AccountPredicateParser.class).to(AccountQueryBuilder.class);
    bind(GroupPredicateParser.class).to(GroupQueryBuilder.class);
  }
}
