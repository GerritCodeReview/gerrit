package com.google.gerrit.server.auth;

/**
 * Defines an abstract request for user authentication to Gerrit.
 *
 */
public abstract class AuthRequest {
  private final String username;
  private String password;

  public AuthRequest(String username) {
    this.username = username;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }
  public void setPassword(String password) {
    this.password = password;
  }
}
