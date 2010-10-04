package com.google.gerrit.httpd.auth.container;

import com.google.inject.servlet.ServletModule;

/** Servlets and support related to CLIENT_SSL_CERT_LDAP authentication. */
public class HttpsClientSslCertModule extends ServletModule {
  @Override
  protected void configureServlets() {
    filter("/").through(HttpsClientSslCertAuthFilter.class);
  }
}
