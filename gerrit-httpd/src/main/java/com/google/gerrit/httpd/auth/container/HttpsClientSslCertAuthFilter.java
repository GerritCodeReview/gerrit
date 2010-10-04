package com.google.gerrit.httpd.auth.container;

import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.AuthResult;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class HttpsClientSslCertAuthFilter implements Filter {

  private static final Pattern REGEX_USERID = Pattern.compile("CN=([^,]*),.*");
  private static final Logger log =
    LoggerFactory.getLogger(HttpsClientSslCertAuthFilter.class);

  private final Provider<WebSession> webSession;
  private final AccountManager accountManager;

  @Inject
  HttpsClientSslCertAuthFilter(final Provider<WebSession> webSession,
      final AccountManager accountManager) {
    this.webSession = webSession;
    this.accountManager = accountManager;
  }

  @Override
  public void destroy() {
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse rsp,
      FilterChain chain) throws IOException, ServletException {
    X509Certificate[] certs = (X509Certificate[]) req.getAttribute("javax.servlet.request.X509Certificate");
    String name = certs[0].getSubjectDN().getName();
    Matcher m = REGEX_USERID.matcher(name);
    String userName = null;
    if (m.matches()) {
      userName = m.group(1);
    } else {
      throw new ServletException("Couldn't extract username from your certificate");
    }
    final AuthRequest areq = AuthRequest.forUser(userName);
    final AuthResult arsp;
    try {
      arsp = accountManager.authenticate(areq);
    } catch (AccountException e) {
      String err = "Unable to authenticate user \"" + userName + "\"";
      log.error(err, e);
      throw new ServletException(err, e);
    }
    webSession.get().login(arsp, true);
    chain.doFilter(req, rsp);
  }

  @Override
  public void init(FilterConfig arg0) throws ServletException {
  }
}
