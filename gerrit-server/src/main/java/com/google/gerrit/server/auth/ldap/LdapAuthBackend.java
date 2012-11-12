package com.google.gerrit.server.auth.ldap;

import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.auth.AuthBackend;
import com.google.gerrit.server.auth.AuthException;
import com.google.gerrit.server.auth.AuthRequest;
import com.google.gerrit.server.auth.AuthUser;
import com.google.gerrit.server.auth.InvalidCredentialsException;
import com.google.gerrit.server.auth.MissingCredentialsException;
import com.google.gerrit.server.auth.UnknownUserException;
import com.google.gerrit.server.auth.UserNotAllowedException;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.security.auth.login.LoginException;

/**
 * Implementation of AuthBackend for the LDAP authentication system.
 */
public class LdapAuthBackend implements AuthBackend {
  private static final Logger log = LoggerFactory.getLogger(LdapAuthBackend.class);

  private final Helper helper;
  private final AuthConfig authConfig;
  private final Config config;

  @Inject
  public LdapAuthBackend(Helper helper,
      AuthConfig authConfig,
      @GerritServerConfig Config config) {
    this.helper = helper;
    this.authConfig = authConfig;
    this.config = config;
  }

  @Override
  public String getDomain() {
    return "ldap";
  }

  @Override
  public AuthUser authenticate(AuthRequest req)
      throws MissingCredentialsException, InvalidCredentialsException,
      UnknownUserException, UserNotAllowedException, AuthException {
    if (req.getUsername() == null) {
      throw new MissingCredentialsException();
    }

    final String username;
    if (config.getBoolean("ldap", "localUsernameToLowerCase", false)) {
      username = req.getUsername().toLowerCase(Locale.US);
    } else {
      username = req.getUsername();
    }

    try {
      final DirContext ctx;
      if (authConfig.getAuthType() == AuthType.LDAP_BIND) {
        ctx = helper.authenticate(username, req.getPassword());
      } else {
        ctx = helper.open();
      }
      try {
        final Helper.LdapSchema schema = helper.getSchema(ctx);
        final LdapQuery.Result m = helper.findAccount(schema, ctx, username);

        if (authConfig.getAuthType() == AuthType.LDAP) {
          // We found the user account, but we need to verify
          // the password matches it before we can continue.
          //
          helper.authenticate(m.getDN(), req.getPassword());
        }
        return new AuthUser(new AuthUser.UUID(username), username);
      } finally {
        try {
          ctx.close();
        } catch (NamingException e) {
          log.warn("Cannot close LDAP query handle", e);
        }
      }
    } catch (AccountException e) {
      log.error("Cannot query LDAP to autenticate user", e);
      throw new InvalidCredentialsException("Cannot query LDAP for account", e);
    } catch (NamingException e) {
      log.error("Cannot query LDAP to autenticate user", e);
      throw new AuthException("Cannot query LDAP for account", e);
    } catch (LoginException e) {
      log.error("Cannot authenticate server via JAAS", e);
      throw new AuthException("Cannot query LDAP for account", e);
    }
  }
}
