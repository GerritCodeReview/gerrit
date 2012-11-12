// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.change;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NOT_IMPLEMENTED;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.httpd.RestApiServlet;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ListChanges;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.util.List;

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class ChangesRestApiServlet extends RestApiServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger log = LoggerFactory.getLogger(ChangesRestApiServlet.class);
  private static final int SC_UNPROCESSABLE_ENTITY = 422;
  private final ParameterParser paramParser;
  private final Provider<ListChanges> factory;
  private final Provider<ReviewDb> db;
  private final ChangeControl.Factory changeControlFactory;

  @Inject
  ChangesRestApiServlet(final Provider<CurrentUser> currentUser,
      ParameterParser paramParser, Provider<ListChanges> ls,
      Provider<ReviewDb> db,
      ChangeControl.Factory changeControlFactory) {
    super(currentUser);
    this.paramParser = paramParser;
    this.factory = ls;
    this.db = db;
    this.changeControlFactory = changeControlFactory;
  }

  protected void handle(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    if (isGET(req) && Strings.isNullOrEmpty(req.getPathInfo())) {
      returnChanges(req, res, null);
      return;
    }

    ChangeResource id = new ChangeResource(req.getPathInfo());
    try {
      List<Change> changes = db.get().changes().byBranchKey(
          new Branch.NameKey(new Project.NameKey(id.project), id.branch),
          new Change.Key(id.changeId)).toList();
      if (changes.isEmpty()) {
        throw new NoSuchChangeException();
      }

      ChangeControl control = changeControlFactory.validateFor(changes.get(0));
      if (isGET(req) && id.rest.isEmpty()) {
        returnChanges(req, res, changes);
        return;
      }
      if (changes.size() >= 2) {
        throw new DuplicateChangeException();
      }

      sendError(res, SC_NOT_IMPLEMENTED,
          Joiner.on('/').join(id.rest) + " not implemented");
    } catch (NoSuchChangeException e) {
      sendError(res, SC_NOT_FOUND, "Not found");
      return;
    } catch (DuplicateChangeException e) {
      sendError(res, SC_UNPROCESSABLE_ENTITY, "Multiple changes match resource");
      return;
    } catch (OrmException e) {
      log.warn("Cannot access changes", e);
      res.sendError(SC_INTERNAL_SERVER_ERROR);
      return;
    }
  }

  private static boolean isGET(HttpServletRequest req) {
    return "GET".equals(req.getMethod());
  }

  private void returnChanges(
      HttpServletRequest req,
      HttpServletResponse res,
      @Nullable List<Change> changes)
      throws IOException {
    ListChanges impl = factory.get();
    if (acceptsJson(req)) {
      impl.setFormat(OutputFormat.JSON_COMPACT);
    }
    if (paramParser.parse(impl, req, res)) {
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      if (impl.getFormat().isJson()) {
        buf.write(JSON_MAGIC);
      }
      Writer out = new BufferedWriter(new OutputStreamWriter(buf, "UTF-8"));
      try {
        if (changes != null) {
          impl.write(changes, out);
        } else {
          impl.query(out);
        }
      } catch (QueryParseException e) {
        sendError(res, SC_BAD_REQUEST, e.getMessage());
        return;
      } catch (OrmException e) {
        log.error("Error querying /changes/", e);
        res.sendError(SC_INTERNAL_SERVER_ERROR);
        return;
      }
      out.flush();

      res.setContentType(impl.getFormat().isJson() ? JSON_TYPE : "text/plain");
      res.setCharacterEncoding("UTF-8");
      send(req, res, buf.toByteArray());
    }
  }

  private static class ChangeResource {
    String project;
    String branch;
    String changeId;
    List<String> rest;

    ChangeResource(String path) {
      if (path.endsWith("/")) {
        path = path.substring(0, path.length() - 1);
      }
      rest = Lists.newArrayList(Splitter.on('/').split(path));
      parseId(rest.remove(0));
    }

    private void parseId(String id) {
      try {
        int t2 = id.lastIndexOf('~');
        int t1 = id.lastIndexOf('~', t2 - 1);
        project = URLDecoder.decode(id.substring(0, t1), "UTF-8");
        branch = URLDecoder.decode(id.substring(t1 + 1, t2), "UTF-8");
        changeId = URLDecoder.decode(id.substring(t2 + 1), "UTF-8");

        if (!branch.startsWith(Constants.R_REFS)) {
          branch = Constants.R_HEADS + branch;
        }
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException("Cannot decode UTF-8", e);
      }
    }
  }

  private static class DuplicateChangeException extends Exception {
    private static final long serialVersionUID = 1L;
  }
}
