package com.google.gerrit.audit;

import com.google.gerrit.common.auth.userpass.LoginResult;

public class LoginResultFormat implements AuditFormatter {
  
  public static final Class<LoginResult> CLASS = LoginResult.class;

  @Override
  public String format(Object result) {
    if(! CLASS.isAssignableFrom(result.getClass()))
        return result.toString();
    
    LoginResult loginResult = (LoginResult) result;
    
    return String.format("%1$s %2$s", loginResult.success,
        loginResult.success ? "":loginResult.getError());
  }

}
