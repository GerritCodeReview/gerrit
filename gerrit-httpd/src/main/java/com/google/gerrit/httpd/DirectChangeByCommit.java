// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.gerrit.httpd;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class DirectChangeByCommit extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger log = LoggerFactory.getLogger(DirectChangeByCommit.class);

  private final Changes changes;

  @Inject
  DirectChangeByCommit(Changes changes) {
    this.changes = changes;
  }

  @Override
  protected void doGet(final HttpServletRequest req, final HttpServletResponse rsp)
      throws IOException {
    String query = CharMatcher.is('/').trimTrailingFrom(req.getPathInfo());
    List<ChangeInfo> results;
    try {
      results = changes.query(query).withLimit(2).get();
    } catch (RestApiException e) {
      log.warn("Cannot process query by URL: /r/" + query, e);
      results = ImmutableList.of();
    }
    String token;
    if (results.size() == 1) {
      // If exactly one change matches, link to that change.
      // TODO Link to a specific patch set, if one matched.
      ChangeInfo ci = results.iterator().next();
      token = PageLinks.toChange(new Project.NameKey(ci.project), new Change.Id(ci._number));
    } else {
      // Otherwise, link to the query page.
      token = PageLinks.toChangeQuery(query);
    }
    UrlModule.toGerrit(token, req, rsp);
  }
}
