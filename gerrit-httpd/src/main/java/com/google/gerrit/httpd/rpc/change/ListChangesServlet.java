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
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ListChanges;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class ListChangesServlet extends RestApiServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger log = LoggerFactory.getLogger(ListChangesServlet.class);
  private final ParameterParser paramParser;
  private final Provider<ListChanges> factory;

  @Inject
  ListChangesServlet(final Provider<CurrentUser> currentUser,
      ParameterParser paramParser, Provider<ListChanges> ls) {
    super(currentUser);
    this.paramParser = paramParser;
    this.factory = ls;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
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
        impl.query(out);
      } catch (QueryParseException e) {
        res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        sendText(req, res, e.getMessage());
        return;
      } catch (OrmException e) {
        log.error("Error querying /changes/", e);
        res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return;
      }
      out.flush();

      res.setContentType(impl.getFormat().isJson() ? JSON_TYPE : "text/plain");
      res.setCharacterEncoding("UTF-8");
      send(req, res, buf.toByteArray());
    }
  }
}
