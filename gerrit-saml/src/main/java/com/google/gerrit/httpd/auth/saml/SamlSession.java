// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.httpd.auth.saml;

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.httpd.CanonicalWebUrl;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.AuthResult;
import com.google.inject.Inject;
import com.google.inject.servlet.SessionScoped;

import org.pac4j.core.context.J2EContext;
import org.pac4j.core.exception.RequiresHttpAction;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.credentials.SAML2Credentials;
import org.pac4j.saml.profile.SAML2Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@SessionScoped
/* SAML 2.0 protocol implementation */
class SamlSession {
  private static final Logger log = LoggerFactory.getLogger(SamlSession.class);
  private final DynamicItem<WebSession> webSession;
  private final AccountManager accountManager;
  private final CanonicalWebUrl canonicalWebUrl;
  private final SAML2Client saml2Client;
  private final SamlConfig samlConfig;
  private String redirectToken;

  @Inject
  SamlSession(DynamicItem<WebSession> webSession,
      AccountManager accountManager, CanonicalWebUrl canonicalWebUrl,
      SAML2Client saml2Client, SamlConfig samlConfig) {
    this.webSession = webSession;
    this.accountManager = accountManager;
    this.canonicalWebUrl = canonicalWebUrl;
    this.saml2Client = saml2Client;
    this.samlConfig = samlConfig;
    this.redirectToken = null;
  }

  @Override
  public String toString() {
    return "SamlSession";
  }

  public void redirectToIdentityProvider(J2EContext context)
      throws RequiresHttpAction {
    redirectToken =
        Url.decode(context
            .getRequest()
            .getRequestURI()
            .substring(
                context.getRequest().getContextPath().length()
                    + SamlWebFilter.GERRIT_LOGIN.length() + 1));
    saml2Client.redirect(context, true, false);
  }

  public void logout() {
    redirectToken = null;
  }

  public void login(J2EContext context) throws RequiresHttpAction, IOException {
    SAML2Credentials credentials = saml2Client.getCredentials(context);
    SAML2Profile user = saml2Client.getUserProfile(credentials, context);
    log.debug("Received SAML callback for userId={} with attributes: {}",
        user.getId(), user.getAttributes());
    AuthRequest areq = new AuthRequest("saml/" + user.getId());
    AuthResult arsp;
    try {
      areq.setUserName(getUserName(user));
      areq.setEmailAddress(getEmailAddress(user));
      areq.setDisplayName(getDisplayName(user));
      log.debug("Authenticated. UserName: {} " + "EmailAddress: {} "
          + "DisplayName: {}", areq.getUserName(), areq.getEmailAddress(),
          areq.getDisplayName());
      arsp = accountManager.authenticate(areq);
    } catch (AccountException e) {
      log.error("Unable to authenticate user \"" + user + "\"", e);
      context.getResponse().sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    webSession.get().login(arsp, true);
    StringBuilder rdr =
        new StringBuilder(canonicalWebUrl.get(context.getRequest()));
    rdr.append(redirectToken == null ? "" : redirectToken);
    context.getResponse().sendRedirect(rdr.toString());
  }

  private String getAttribute(SAML2Profile user, String attrName) {
    List<?> names = (List<?>) user.getAttribute(attrName);
    if (names != null && !names.isEmpty()) {
      return (String) names.get(0);
    }
    return null;
  }

  private String getAttributeOrElseId(SAML2Profile user, String attrName) {
    String value = getAttribute(user, attrName);
    if (value != null) {
      return value;
    }
    return user.getId();
  }

  private String getDisplayName(SAML2Profile user) {
    return getAttributeOrElseId(user, samlConfig.getDisplayNameAttr());
  }

  private String getEmailAddress(SAML2Profile user) {
    String emailAddress = getAttribute(user, samlConfig.getEmailAddressAttr());
    if (emailAddress != null) {
      return emailAddress;
    }
    String nameId = user.getId();
    if (!nameId.contains("@")) {
      log.debug(
          "Email address attribute not found, NameId {} does not look like an email.",
          nameId);
      return null;
    }
    return emailAddress;
  }

  private String getUserName(SAML2Profile user) {
    return getAttribute(user, samlConfig.getUserNameAttr());
  }
}
