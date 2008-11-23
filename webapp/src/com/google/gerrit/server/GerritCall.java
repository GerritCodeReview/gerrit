package com.google.gerrit.server;

import com.google.gerrit.client.Gerrit;
import com.google.gwtjsonrpc.client.CookieAccess;
import com.google.gwtjsonrpc.server.ActiveCall;
import com.google.gwtjsonrpc.server.ValidToken;
import com.google.gwtjsonrpc.server.XsrfException;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class GerritCall extends ActiveCall {
  private final GerritServer server;
  private boolean readAccountCookie;
  private ValidToken accountCookie;

  public GerritCall(final GerritServer gs, final HttpServletRequest i,
      final HttpServletResponse o) {
    super(i, o);
    server = gs;
  }

  @Override
  public String getUser() {
    if (!readAccountCookie) {
      readAccountCookie = true;

      String idstr = CookieAccess.get(Gerrit.ACCOUNT_COOKIE);
      try {
        accountCookie = server.getAccountToken().checkToken(idstr, null);
      } catch (XsrfException e) {
        accountCookie = null;
      }

      if (accountCookie != null && accountCookie.needsRefresh()) {
        try {
          idstr = server.getAccountToken().newToken(accountCookie.getData());
          final Cookie c = new Cookie(Gerrit.ACCOUNT_COOKIE, idstr);
          c.setMaxAge(server.getSessionAge());
          c.setPath(getHttpServletRequest().getContextPath());
          getHttpServletResponse().addCookie(c);
        } catch (XsrfException e) {
        }
      }
    }
    return accountCookie != null ? accountCookie.getData() : null;
  }
}
