// Copyright (C) 2011 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.index;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.lucene.util.Version.LUCENE_32;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountProjectWatch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchLineComment;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryBuilder;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.SingleGroupUser;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import dk.brics.automaton.RegExp;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ChainedFilter;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.FilteredQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermsFilter;
import org.apache.lucene.search.regex.JavaUtilRegexCapabilities;
import org.apache.lucene.search.regex.RegexQuery;
import org.eclipse.jgit.lib.Constants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author sop@google.com (Shawn Pearce)
 *
 */
public class LuceneQueryBuilder extends QueryBuilder<ChangeData> {
  private static final LuceneQueryBuilder.Definition<ChangeData, LuceneQueryBuilder> mydef =
      new QueryBuilder.Definition<ChangeData, LuceneQueryBuilder>(
          LuceneQueryBuilder.class);

  private static final Pattern PAT_LEGACY_ID = Pattern.compile("^[1-9][0-9]*$");
  private static final Pattern PAT_CHANGE_ID =
      Pattern.compile("^[iI][0-9a-f]{4,}.*$");
  private static final Pattern DEF_CHANGE =
      Pattern.compile("^([1-9][0-9]*|[iI][0-9a-f]{4,}.*)$");

  static class Arguments {
    final AllProjectsName allProjectsName;
    final ProjectCache projectCache;
    final GroupCache groupCache;
    final AccountResolver accountResolver;
    final CapabilityControl.Factory capabilityControlFactory;
    final IdentifiedUser.GenericFactory userFactory;
    final Provider<ReviewDb> dbProvider;

    @Inject
    Arguments(AllProjectsName allProjectsName, ProjectCache projectCache,
        GroupCache groupCache, AccountResolver accountResolver,
        CapabilityControl.Factory capabilityControlFactory,
        IdentifiedUser.GenericFactory userFactory, Provider<ReviewDb> dbProvider) {
      this.allProjectsName = allProjectsName;
      this.projectCache = projectCache;
      this.groupCache = groupCache;
      this.accountResolver = accountResolver;
      this.capabilityControlFactory = capabilityControlFactory;
      this.userFactory = userFactory;
      this.dbProvider = dbProvider;
    }
  }

  private final Arguments args;
  private final CurrentUser currentUser;

  @Inject
  LuceneQueryBuilder(Arguments args, CurrentUser user) {
    super(mydef);
    this.args = args;
    this.currentUser = user;
  }

  @Override
  public Predicate<ChangeData> and(Predicate<ChangeData>... that) {
    BooleanQuery b = new BooleanQuery();
    for (Predicate<ChangeData> p : that) {
      b.add(q(p), BooleanClause.Occur.MUST);
    }

    for (BooleanClause q : b) {
      if (q.getQuery() instanceof FilteredQuery) {
        return pushFilterDown(b);
      }
    }

    return p(b);
  }

  private Predicate<ChangeData> pushFilterDown(BooleanQuery src) {
    BooleanQuery bq = new BooleanQuery();
    ArrayList<Filter> filters = new ArrayList<Filter>(4);

    for (BooleanClause clause : src) {
      if (clause.getQuery() instanceof FilteredQuery) {
        FilteredQuery fq = (FilteredQuery) clause.getQuery();
        if (!(fq.getQuery() instanceof MatchAllDocsQuery)) {
          bq.add(fq.getQuery(), BooleanClause.Occur.MUST);
        }
        filters.add(fq.getFilter());
      } else {
        bq.add(clause.getQuery(), BooleanClause.Occur.MUST);
      }
    }

    Query query;
    if (bq.clauses().size() == 1) {
      query = bq.clauses().get(0).getQuery();
    } else {
      query = bq;
    }

    for (Filter f : filters) {
      if (f instanceof ChangeFilter) {
        ((ChangeFilter) f).setQuery(query);
      }
    }

    Filter filter;
    if (filters.size() == 1) {
      filter = filters.get(0);
    } else {
      boolean changeType = true;
      Filter[] all = filters.toArray(new Filter[filters.size()]);
      for (Filter f : all) {
        if (f instanceof ChangeFilter) {
          continue;
        } else {
          changeType = false;
          break;
        }
      }

      if (changeType) {
        filter = ChangeFilter.chain(all);
      } else {
        filter = new ChainedFilter(all, ChainedFilter.AND);
      }
    }

    return p(new FilteredQuery(query, filter));
  }

  @Override
  protected Predicate<ChangeData> or(Predicate<ChangeData>... that) {
    BooleanQuery b = new BooleanQuery();
    for (Predicate<ChangeData> p : that) {
      b.add(q(p), BooleanClause.Occur.SHOULD);
    }
    return p(b);
  }

  @Override
  protected Predicate<ChangeData> not(Predicate<ChangeData> that) {
    BooleanQuery b = new BooleanQuery();
    b.add(q(that), BooleanClause.Occur.MUST_NOT);
    return p(b);
  }

  @Operator
  public Predicate<ChangeData> age(String query) {
    long now = System.currentTimeMillis();
    long s = ConfigUtil.getTimeUnit(query, 0, SECONDS);
    long ms = MILLISECONDS.convert(s, SECONDS);
    int cut = UpdateTransaction.encodeLastUpdated(now - ms);
    return p(NumericRangeQuery.newIntRange("last-update", cut, null, false,
        false));
  }

  @Operator
  public Predicate<ChangeData> change(String query) {
    if (PAT_LEGACY_ID.matcher(query).matches()) {
      return eq("change-id", query);

    } else if (PAT_CHANGE_ID.matcher(query).matches()) {
      if (query.charAt(0) == 'i') {
        query = "I" + query.substring(1);
      }
      if (query.length() == 41) {
        return eq("change-key", query);
      } else {
        return prefix("change-key", query);
      }
    }

    throw new IllegalArgumentException();
  }

  @Operator
  public Predicate<ChangeData> ref(String ref) {
    if (ref.startsWith("^")) {
      return re("ref", ref);
    }
    return eq("ref", ref);
  }

  @Operator
  public Predicate<ChangeData> branch(String branch) {
    if (branch.startsWith("^")) {
      return re("ref", "^" + Constants.R_HEADS + branch.substring(1));
    }
    return eq("ref", Constants.R_HEADS + branch);
  }

  @Operator
  public Predicate<ChangeData> topic(String name) {
    if (name.startsWith("^")) {
      return re("topic", name);
    }
    return eq("topic", name);
  }

  @Operator
  public Predicate<ChangeData> project(String name) {
    if (name.startsWith("^")) {
      return re("project", name);
    }
    return eq("project", name);
  }

  @SuppressWarnings("unchecked")
  @Operator
  public Predicate<ChangeData> status(String statusName) {
    if ("new".equals(statusName)) {
      return eq("status", String.valueOf(Change.Status.NEW.getCode()));

    } else if ("submitted".equals(statusName)) {
      return eq("status", String.valueOf(Change.Status.SUBMITTED.getCode()));

    } else if ("merged".equals(statusName)) {
      return eq("status", String.valueOf(Change.Status.MERGED.getCode()));

    } else if ("abandoned".equals(statusName)) {
      return eq("status", String.valueOf(Change.Status.ABANDONED.getCode()));

    } else if ("open".equals(statusName)) {
      return or(eq("status", String.valueOf(Change.Status.NEW.getCode())), eq(
          "status", String.valueOf(Change.Status.SUBMITTED.getCode())));

    } else if ("closed".equals(statusName)) {
      return or(eq("status", String.valueOf(Change.Status.MERGED.getCode())),
          eq("status", String.valueOf(Change.Status.ABANDONED.getCode())));

    } else if ("reviewed".equalsIgnoreCase(statusName)) {
      throw new IllegalArgumentException("TODO reviewed status");

    } else {
      throw new IllegalArgumentException();
    }
  }

  @Operator
  public Predicate<ChangeData> is(String value) {
    if ("starred".equalsIgnoreCase(value)) {
      return starredby(currentUser);
    }

    if ("watched".equalsIgnoreCase(value)) {
      return watchedby(currentUser);
    }

    if ("visible".equalsIgnoreCase(value)) {
      return visibleto(currentUser);
    }

    return status(value);
  }

  @Operator
  public Predicate<ChangeData> has(String value) throws OrmException {
    if ("star".equalsIgnoreCase(value)) {
      return starredby(currentUser);
    }

    if ("draft".equalsIgnoreCase(value)) {
      if (currentUser instanceof IdentifiedUser) {
        HashSet<Change.Id> ids = new HashSet<Change.Id>();
        for (PatchLineComment sc : args.dbProvider.get().patchComments()
            .draftByAuthor(((IdentifiedUser) currentUser).getAccountId())) {
          ids.add(sc.getKey().getParentKey().getParentKey().getParentKey());
        }
        return any(ids);
      }
    }

    throw new IllegalArgumentException();
  }

  @Operator
  public Predicate<ChangeData> starredby(String who)
      throws QueryParseException, OrmException {
    Account account = args.accountResolver.find(who);
    if (account == null) {
      throw error("User " + who + " not found");
    }
    return starredby(args.userFactory.create(args.dbProvider, account.getId()));
  }

  private Predicate<ChangeData> starredby(CurrentUser user) {
    return any(user.getStarredChanges());
  }

  private Predicate<ChangeData> any(Set<Change.Id> ids) {
    if (ids.isEmpty()) {
      return p(new NoDocumentsQuery());
    }
    if (ids.size() == 1) {
      return eq("change-id", ids.iterator().next().toString());
    }
    TermsFilter filter = new TermsFilter();
    for (Change.Id id : ids) {
      filter.addTerm(new Term("change-id", id.toString()));
    }
    return p(new FilteredQuery(new MatchAllDocsQuery(), filter));
  }

  @Operator
  public Predicate<ChangeData> watchedby(String who)
      throws QueryParseException, OrmException {
    Account account = args.accountResolver.find(who);
    if (account == null) {
      throw error("User " + who + " not found");
    }
    return watchedby(args.userFactory.create(args.dbProvider, account.getId()));
  }

  private Predicate<ChangeData> watchedby(CurrentUser user) {
    Collection<AccountProjectWatch> filters = user.getNotificationFilters();
    if (filters.isEmpty()) {
      return p(new NoDocumentsQuery());
    } else if (filters.size() == 1) {
      return p(compile(filters.iterator().next()));
    } else {
      BooleanQuery b = new BooleanQuery();
      for (AccountProjectWatch filter : filters) {
        b.add(compile(filter), BooleanClause.Occur.SHOULD);
      }
      return p(b);
    }
  }

  private Query compile(AccountProjectWatch filter) {
    Project.NameKey name = filter.getProjectNameKey();
    Query project;
    if (args.allProjectsName.equals(name)) {
      project = new MatchAllDocsQuery();
    } else {
      project = new TermQuery(new Term("project", name.get()));
    }

    if (filter.getFilter() != null && !filter.getFilter().isEmpty()) {
      try {
        BooleanQuery b = new BooleanQuery();
        b.add(project, BooleanClause.Occur.MUST);
        b.add(q(parse(filter.getFilter())), BooleanClause.Occur.MUST);
        return b;
      } catch (QueryParseException e) {
        return new NoDocumentsQuery();
      }
    } else {
      return project;
    }
  }

  @Operator
  public Predicate<ChangeData> visibleto(String who)
      throws QueryParseException, OrmException {
    Account account = args.accountResolver.find(who);
    if (account != null) {
      return visibleto(args.userFactory
          .create(args.dbProvider, account.getId()));
    }

    // If its not an account, maybe its a group?
    //
    AccountGroup g = args.groupCache.get(new AccountGroup.NameKey(who));
    if (g != null) {
      return visibleto(new SingleGroupUser(args.capabilityControlFactory, g
          .getGroupUUID()));
    }

    Collection<AccountGroup> matches =
        args.groupCache.get(new AccountGroup.ExternalNameKey(who));
    if (matches != null && !matches.isEmpty()) {
      HashSet<AccountGroup.UUID> ids = new HashSet<AccountGroup.UUID>();
      for (AccountGroup group : matches) {
        ids.add(group.getGroupUUID());
      }
      return visibleto(new SingleGroupUser(args.capabilityControlFactory, ids));
    }

    throw error("No user or group matches \"" + who + "\".");
  }

  public Predicate<ChangeData> is_visible() {
    return visibleto(currentUser);
  }

  private Predicate<ChangeData> visibleto(CurrentUser who) {
    return new LucenePredicate(new FilteredQuery(new MatchAllDocsQuery(),
        new VisibleFilter(new MatchAllDocsQuery(), args.projectCache, who)));
  }

  @Operator
  public Predicate<ChangeData> subject(String query) {
    try {
      QueryParser qp =
          new QueryParser(LUCENE_32, "subject", new StandardAnalyzer(LUCENE_32));
      return p(qp.parse(query));
    } catch (ParseException e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    }
  }

  @Operator
  public Predicate<ChangeData> owner(String who) throws QueryParseException,
      OrmException {
    Set<Account.Id> m = args.accountResolver.findAll(who);
    if (m.isEmpty()) {
      throw error("User " + who + " not found");
    } else if (m.size() == 1) {
      Account.Id id = m.iterator().next();
      return eq("owner", id.toString());
    } else {
      BooleanQuery b = new BooleanQuery();
      for (Account.Id id : m) {
        b.add(new TermQuery(new Term("owner", id.toString())),
            BooleanClause.Occur.SHOULD);
      }
      return p(b);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  protected Predicate<ChangeData> defaultField(String query)
      throws QueryParseException {
    if (query.startsWith("refs/")) {
      return ref(query);

    } else if (DEF_CHANGE.matcher(query).matches()) {
      return change(query);

    } else {
      throw error("Unsupported query:" + query);
    }
  }

  private static LucenePredicate eq(String field, String value) {
    return p(new TermQuery(new Term(field, value)));
  }

  private static LucenePredicate re(String field, String value) {
    RegexQuery q = new RegexQuery(new Term(field, value));
    q.setRegexImplementation(new JavaUtilRegexCapabilities() {
      private String prefix;

      @Override
      public void compile(String pattern) {
        super.compile(pattern);

        String re = pattern;
        if (re.startsWith("^")) {
          re = re.substring(1);
        }
        if (re.endsWith("$") && !re.endsWith("\\$")) {
          re = re.substring(0, re.length() - 1);
        }
        try {
          prefix = new RegExp(re).toAutomaton().getCommonPrefix();
        } catch (RuntimeException err) {
          prefix = null;
        }
      }

      @Override
      public String prefix() {
        return prefix;
      }
    });
    return p(q);
  }

  private static LucenePredicate prefix(String field, String value) {
    return p(new PrefixQuery(new Term(field, value + "*")));
  }

  private static Query q(Predicate<ChangeData> that) {
    return ((LucenePredicate) that).getQuery();
  }

  private static LucenePredicate p(Query q) {
    return new LucenePredicate(q);
  }
}
