package com.google.gerrit.server.auth;

/**
 * Defines an abstract request for user authentication to Gerrit.
 *
 */
public abstract class AuthRequest {

  /**
   * Returns the username to be authenticated.
   *
   * @return username for authentication.
   */
  public abstract String getUsername();

  /**
   * Returns the user's credentials
   *
   * @return user's credentials or null
   */
  public abstract String getPassword();
}
