package com.google.gerrit.server.http;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.http.WebSessionManager.Key;
import com.google.gerrit.server.http.WebSessionManager.Val;
import com.google.inject.Inject;
import com.google.inject.servlet.RequestScoped;

import net.sf.ehcache.Element;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RequestScoped
public final class WebSession {
  private static final String ACCOUNT_COOKIE = "GerritAccount";

  private final HttpServletRequest request;
  private final HttpServletResponse response;
  private final WebSessionManager manager;
  private final AnonymousUser anonymous;
  private final IdentifiedUser.RequestFactory identified;
  private Cookie outCookie;
  private Element element;

  @Inject
  WebSession(final HttpServletRequest request,
      final HttpServletResponse response, final WebSessionManager manager,
      final AnonymousUser anonymous,
      final IdentifiedUser.RequestFactory identified) {
    this.request = request;
    this.response = response;
    this.manager = manager;
    this.anonymous = anonymous;
    this.identified = identified;
    this.element = manager.get(readCookie());

    if (isSignedIn() && val().refreshCookieAt <= System.currentTimeMillis()) {
      // Cookie is more than half old. Send it again to the client with a
      // fresh expiration date.
      //
      final int age;
      age = element.getTimeToIdle();
      val().refreshCookieAt = System.currentTimeMillis() + (age / 2 * 1000L);
      saveCookie(key().token, age);
    }
  }

  private String readCookie() {
    final Cookie[] all = request.getCookies();
    if (all != null) {
      for (final Cookie c : all) {
        if (ACCOUNT_COOKIE.equals(c.getName())) {
          final String v = c.getValue();
          return v != null && !"".equals(v) ? v : null;
        }
      }
    }
    return null;
  }

  public boolean isSignedIn() {
    return element != null;
  }

  String getToken() {
    return isSignedIn() ? key().token : null;
  }

  boolean isTokenValid(final String keyIn) {
    return isSignedIn() && key().token.equals(keyIn);
  }

  CurrentUser getCurrentUser() {
    return isSignedIn() ? identified.create(val().accountId) : anonymous;
  }

  public void login(final Account.Id id, final boolean rememberMe) {
    logout();
    element = manager.create(id);
    final int age;
    if (rememberMe) {
      age = element.getTimeToIdle();
      val().refreshCookieAt = System.currentTimeMillis() + (age / 2 * 1000L);
    } else {
      age = -1 /* don't store on client disk */;
      val().refreshCookieAt = Long.MAX_VALUE;
    }
    saveCookie(key().token, age);
  }

  public void logout() {
    if (element != null) {
      manager.destroy(element);
      element = null;
      saveCookie("", 0 /* erase at client */);
    }
  }

  private Key key() {
    return ((Key) element.getKey());
  }

  private Val val() {
    return ((Val) element.getObjectValue());
  }

  private void saveCookie(final String val, final int age) {
    if (outCookie == null) {
      String path = request.getContextPath();
      if (path.equals("")) {
        path = "/";
      }
      outCookie = new Cookie(ACCOUNT_COOKIE, val);
      outCookie.setPath(path);
      outCookie.setMaxAge(age);
      response.addCookie(outCookie);
    } else {
      outCookie.setMaxAge(age);
      outCookie.setValue(val);
    }
  }
}
