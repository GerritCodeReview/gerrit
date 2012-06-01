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

import com.google.gerrit.httpd.RestApiServlet;
import com.google.gerrit.server.CurrentUser;
import com.google.gwtorm.server.OrmException;
import com.google.gerrit.server.changedetail.AbandonChange;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class AbandonChangeServlet extends RestApiServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger log =
      LoggerFactory.getLogger(AbandonChangeServlet.class);
  private final ParameterParser paramParser;
  private final Provider<AbandonChange> abandonChangeFactory;

  @Inject
  AbandonChangeServlet(final Provider<CurrentUser> currentUser,
      final ParameterParser paramParser,
      final Provider<AbandonChange> abandonChangeFactory) {
    super(currentUser);
    this.paramParser = paramParser;
    this.abandonChangeFactory = abandonChangeFactory;
  }

  @Override
  protected void doPost(final HttpServletRequest req,
                        final HttpServletResponse res)
      throws IOException {

    try {
      final AbandonChange impl = abandonChangeFactory.get();
      if (!acceptsJson(req)) {
      }
      if (acceptsJson(req) && paramParser.parse(impl, req, res)) {
        sendJson(req, res, impl.call());
      }
    } catch (EmailException e) {
      log.error("Email error accessing " + req.getServletPath()
                + req.getPathInfo(), e);
      res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (InvalidChangeOperationException e) {
      log.error("Operation error accessing " + req.getServletPath()
                + req.getPathInfo(), e);
      res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (NoSuchChangeException e) {
      log.error("No such change error accessing " + req.getServletPath()
                + req.getPathInfo(), e);
      res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (OrmException e) {
      log.error("Orm error accessing " + req.getServletPath()
                + req.getPathInfo(), e);
      res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }
}
