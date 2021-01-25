package com.google.gerrit.httpd.auth.restapi;

import static com.google.gerrit.server.account.AccountResource.ACCOUNT_KIND;

import com.google.gerrit.extensions.restapi.RestApiModule;

public class OAuthRestModule extends RestApiModule {
  @Override
  protected void configure() {
    get(ACCOUNT_KIND, "oauthtoken").to(GetOAuthToken.class);
  }
}
