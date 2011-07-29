package com.google.gerrit.server.auth.crowd;

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroup.UUID;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.atlassian.crowd.exception.ApplicationPermissionException;
import com.atlassian.crowd.exception.ExpiredCredentialException;
import com.atlassian.crowd.exception.GroupNotFoundException;
import com.atlassian.crowd.exception.InactiveAccountException;
import com.atlassian.crowd.exception.InvalidAuthenticationException;
import com.atlassian.crowd.exception.OperationFailedException;
import com.atlassian.crowd.exception.UserNotFoundException;
import com.atlassian.crowd.integration.rest.service.factory.RestCrowdClientFactory;
import com.atlassian.crowd.model.group.Group;
import com.atlassian.crowd.model.user.User;
import com.atlassian.crowd.service.client.CrowdClient;

import org.apache.commons.lang.Validate;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Singleton
class CrowdHelper {
  static final Logger log = LoggerFactory.getLogger(CrowdHelper.class);

  private final CrowdClient crowdClient;

  private static final String INVALID_CREDENTIALS_MSG = "Invalid credentials for querying Crowd";
  private static final String UNKNOWN_CROWD_ERROR_MSG = "An unknown error occured while querying the Crowd server";

  @Inject
  CrowdHelper(@GerritServerConfig final Config config) {

    final String restUrl = config.getString("auth", null, "crowdRestUrl");
    final String crowdUsername = config.getString("auth", null, "crowdUsername");
    final String crowdPassword = config.getString("auth", null, "crowdPassword");

    Validate.notNull(restUrl, "auth.crowdRestUrl must be defined");
    Validate.notNull(crowdUsername, "auth.crowdUsername must be defined");
    Validate.notNull(crowdPassword, "auth.crowdPassword must be defined");

    crowdClient = new RestCrowdClientFactory().newInstance(restUrl, crowdUsername, crowdPassword);
  }

  public User authenticate(String username, String password) throws AccountException {
    try {
      return crowdClient.authenticateUser(username, password);
    } catch (ApplicationPermissionException e) {
      log.error(INVALID_CREDENTIALS_MSG, e);
      throw new AccountException(INVALID_CREDENTIALS_MSG, e);
    } catch (UserNotFoundException e) {
      throw new AccountException("Unknown user", e);
    } catch (InactiveAccountException e) {
      throw new AccountException("Account inactive", e);
    } catch (ExpiredCredentialException e) {
      throw new AccountException("Credentials expired", e);
    } catch (InvalidAuthenticationException e) {
      log.error(INVALID_CREDENTIALS_MSG, e);
      throw new AccountException(INVALID_CREDENTIALS_MSG, e);
    } catch (OperationFailedException e) {
      throw new AccountException(UNKNOWN_CROWD_ERROR_MSG, e);
    }
  }

  public Set<UUID> groups(String username) {
    final HashSet<AccountGroup.UUID> r = new HashSet<AccountGroup.UUID>();

    List<Group> groups;
    try {
      final int MAX_GROUPS = 1000; // If a user has more than 1000 groups, tough!
      groups = crowdClient.getGroupsForNestedUser(username, 0, MAX_GROUPS);

      for (Group g : groups) {
        r.add(new UUID("crowd:" + g.getName()));
      }
    } catch (ApplicationPermissionException e) {
      log.error(INVALID_CREDENTIALS_MSG, e);
    } catch (UserNotFoundException e) {
      log.warn("Unable to find user '" + username + "' in Crowd", e);
    } catch (InvalidAuthenticationException e) {
      log.error(INVALID_CREDENTIALS_MSG, e);
    } catch (OperationFailedException e) {
      log.warn(UNKNOWN_CROWD_ERROR_MSG, e);
    }

    return r;
  }

  public Group groupByName(String name) {
    try {
      return crowdClient.getGroup(name);
    } catch (GroupNotFoundException e) {
      // Ignore, we will just return null
    } catch (OperationFailedException e) {
      log.warn(UNKNOWN_CROWD_ERROR_MSG, e);
    } catch (InvalidAuthenticationException e) {
      log.error(INVALID_CREDENTIALS_MSG, e);
    } catch (ApplicationPermissionException e) {
      log.error(INVALID_CREDENTIALS_MSG, e);
    }
    return null;
  }

}
