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

package com.google.gerrit.httpd;

import com.google.gerrit.httpd.RestTokenVerifier.InvalidTokenException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class TokenVerifiedRestApiServlet extends RestApiServlet {
  private static final long serialVersionUID = 1L;
  private final Provider<CurrentUser> currentUser;
  private final RestTokenVerifier verifier;

  @Inject
  protected TokenVerifiedRestApiServlet(Provider<CurrentUser> currentUser,
      RestTokenVerifier verifier) {
    super(currentUser);
    this.currentUser = currentUser;
    this.verifier = verifier;
  }

  @Override
  protected final void doGet(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    CurrentUser user = currentUser.get();
    if (user instanceof IdentifiedUser) {
      Account.Id userId = ((IdentifiedUser) user).getAccountId();
      TokenInfo info = new TokenInfo();
      info._authKey = verifier.sign(userId, getReqUrl(req));

      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      buf.write(JSON_MAGIC);
      buf.write(new Gson().toJson(info).getBytes("UTF-8"));
      res.setContentType(JSON_TYPE);
      res.setCharacterEncoding("UTF-8");
      send(req, res, buf.toByteArray());
    } else {
      res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
  }

  @Override
  protected final void doPost(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    JsonParser parser = new JsonParser();
    JsonObject json = (JsonObject) parser.parse(req.getReader());
    String tokenString = json.get("_authKey").getAsString();

    CurrentUser user = currentUser.get();
    Account.Id userId;
    if (user instanceof IdentifiedUser) {
      userId = ((IdentifiedUser) user).getAccountId();
    } else {
      res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    try {
      verifier.verify(userId, getReqUrl(req), tokenString);
    } catch (InvalidTokenException err) {
      handleError(err, req, res);
      return;
    }
    doRequest(req, res, json);
  }

  protected abstract void doRequest(HttpServletRequest req,
      HttpServletResponse res, JsonObject json) throws IOException;

  private static String getReqUrl(HttpServletRequest req) {
    StringBuffer url = req.getRequestURL();
    if (req.getQueryString() != null) {
      url.append('?').append(req.getQueryString());
    }
    return url.toString();
  }

  private static class TokenInfo {
    @SuppressWarnings("unused")
    String _authKey;
  }
}
