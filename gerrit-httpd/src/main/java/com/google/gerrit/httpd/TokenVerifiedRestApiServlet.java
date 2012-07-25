package com.google.gerrit.httpd;

import com.google.gerrit.httpd.RestTokenVerifier.InvalidTokenException;
import com.google.gerrit.httpd.RestTokenVerifier.ParsedToken;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.BufferedReader;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TokenVerifiedRestApiServlet extends RestApiServlet {
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
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    String token = "token=" +
        verifier.encode(currentUser.get().getUserName(), getReqUrl(req));
    sendText(req, res, token);
  }

  protected void checkToken(HttpServletRequest req, HttpServletResponse res)
      throws InvalidTokenException {
    String tokenString = req.getParameter("token");
    ParsedToken token;
    token = verifier.decode(tokenString);
    if (!token.getUser().equals(currentUser.get().getUserName())
        || !token.getUrl().equals(getReqUrl(req))) {
      throw new InvalidTokenException();
    }
    // Everything is OK
  }

  private String getReqUrl(HttpServletRequest req) {
    String reqUrl = req.getRequestURL().toString();
    if (req.getQueryString() != null) {
      reqUrl += "?" + req.getQueryString();
    }
    return reqUrl;
  }
}
