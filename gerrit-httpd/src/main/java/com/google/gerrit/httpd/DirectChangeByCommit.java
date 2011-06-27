// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.gerrit.httpd;

import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryRewriter;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
class DirectChangeByCommit extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger log =
      LoggerFactory.getLogger(DirectChangeByCommit.class);

  private final ChangeQueryBuilder.Factory queryBuilder;
  private final Provider<ChangeQueryRewriter> queryRewriter;
  private final Provider<CurrentUser> currentUser;

  @Inject
  DirectChangeByCommit(ChangeQueryBuilder.Factory queryBuilder,
      Provider<ChangeQueryRewriter> queryRewriter,
      Provider<CurrentUser> currentUser) {
    this.queryBuilder = queryBuilder;
    this.queryRewriter = queryRewriter;
    this.currentUser = currentUser;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void doGet(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    String query = req.getPathInfo();
    HashSet<Change.Id> ids = new HashSet<Change.Id>();
    try {
      ChangeQueryBuilder builder = queryBuilder.create(currentUser.get());
      Predicate<ChangeData> visibleToMe = builder.is_visible();
      Predicate<ChangeData> q = builder.parse(query);
      q = Predicate.and(q, builder.sortkey_before("z"), builder.limit(2), visibleToMe);

      ChangeQueryRewriter rewriter = queryRewriter.get();
      Predicate<ChangeData> s = rewriter.rewrite(q);
      if (!(s instanceof ChangeDataSource)) {
        s = rewriter.rewrite(Predicate.and(builder.status_open(), q));
      }

      if (s instanceof ChangeDataSource) {
        for (ChangeData d : ((ChangeDataSource) s).read()) {
          ids.add(d.getId());
        }
      }
    } catch (QueryParseException e) {
      log.warn("Received invalid query by URL: /r/" + query, e);
    } catch (OrmException e) {
      log.warn("Cannot process query by URL: /r/" + query, e);
    }

    String token;
    if (ids.size() == 1) {
      // If exactly one change matches, link to that change.
      // TODO Link to a specific patch set, if one matched.
      token = PageLinks.toChange(ids.iterator().next());

    } else {
      // Otherwise, link to the query page.
      token = PageLinks.toChangeQuery(query);
    }
    UrlModule.toGerrit(token, req, rsp);
  }
}
