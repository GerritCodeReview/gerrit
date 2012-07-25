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
import com.google.gerrit.httpd.RestTokenVerifier.ParsedToken;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;
import com.google.inject.Provider;

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
      String token = "_token=" + verifier.encode(userId, getReqUrl(req));
      sendText(req, res, token);
    } else {
      res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
  }

  protected final void doPost(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    String tokenString = req.getParameter("_token").replace(' ', '+');
    ParsedToken token;
    try {
      token = verifier.decode(tokenString);
    } catch (InvalidTokenException err) {
      handleError(err, req, res);
      return;
    }
    CurrentUser user = currentUser.get();
    if (user instanceof IdentifiedUser) {
      Account.Id userId = ((IdentifiedUser) user).getAccountId();
      if (!token.getUser().equals(userId)
          || !token.getUrl().equals(getReqUrl(req))) {
        handleError(new InvalidTokenException(), req, res);
        return;
      }
      doRequest(req, res);
    } else {
      res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
  }

  abstract public void doRequest(HttpServletRequest req, HttpServletResponse res)
      throws IOException;

  private String getReqUrl(HttpServletRequest req) {
    String reqUrl = req.getRequestURL().toString();
    if (req.getQueryString() != null) {
      reqUrl += "?" + req.getQueryString();
    }
    return reqUrl;
  }
}
