package com.google.gerrit.httpd.raw;

import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.util.http.CacheHeaders;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Offers a dedicated endpoint for checking if a user is still logged in. Returns {@code 204
 * NO_CONTENT} for logged-in users, {@code 403 FORBIDDEN} otherwise.
 *
 * <p>Mainly used by PolyGerrit to check if a user is still logged in.
 */
public class AuthorizationCheckServlet extends HttpServlet {
  private final Provider<CurrentUser> user;

  @Inject
  AuthorizationCheckServlet(Provider<CurrentUser> user) {
    this.user = user;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    CacheHeaders.setNotCacheable(res);
    if (user.get().isIdentifiedUser()) {
      res.setStatus(HttpServletResponse.SC_NO_CONTENT);
    } else {
      res.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
  }
}
