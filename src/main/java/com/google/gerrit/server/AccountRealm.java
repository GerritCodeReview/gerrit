package com.google.gerrit.server;

import org.jsecurity.authc.AuthenticationException;
import org.jsecurity.authc.AuthenticationInfo;
import org.jsecurity.authc.AuthenticationToken;
import org.jsecurity.authz.AuthorizationException;
import org.jsecurity.authz.Permission;
import org.jsecurity.realm.Realm;
import org.jsecurity.subject.PrincipalCollection;

import java.util.Collection;
import java.util.List;

public class AccountRealm implements Realm {

  public AuthenticationInfo getAuthenticationInfo(AuthenticationToken arg0)
      throws AuthenticationException {
    // TODO Auto-generated method stub
    return null;
  }

  public String getName() {
    // TODO Auto-generated method stub
    return null;
  }

  public boolean supports(AuthenticationToken arg0) {
    // TODO Auto-generated method stub
    return false;
  }

  public void checkPermission(PrincipalCollection arg0, String arg1)
      throws AuthorizationException {
    // TODO Auto-generated method stub

  }

  public void checkPermission(PrincipalCollection arg0, Permission arg1)
      throws AuthorizationException {
    // TODO Auto-generated method stub

  }

  public void checkPermissions(PrincipalCollection arg0, String... arg1)
      throws AuthorizationException {
    // TODO Auto-generated method stub

  }

  public void checkPermissions(PrincipalCollection arg0,
      Collection<Permission> arg1) throws AuthorizationException {
    // TODO Auto-generated method stub

  }

  public void checkRole(PrincipalCollection arg0, String arg1)
      throws AuthorizationException {
    // TODO Auto-generated method stub

  }

  public void checkRoles(PrincipalCollection arg0, Collection<String> arg1)
      throws AuthorizationException {
    // TODO Auto-generated method stub

  }

  public boolean hasAllRoles(PrincipalCollection arg0, Collection<String> arg1) {
    // TODO Auto-generated method stub
    return false;
  }

  public boolean hasRole(PrincipalCollection arg0, String arg1) {
    // TODO Auto-generated method stub
    return false;
  }

  public boolean[] hasRoles(PrincipalCollection arg0, List<String> arg1) {
    // TODO Auto-generated method stub
    return null;
  }

  public boolean isPermitted(PrincipalCollection arg0, String arg1) {
    // TODO Auto-generated method stub
    return false;
  }

  public boolean isPermitted(PrincipalCollection arg0, Permission arg1) {
    // TODO Auto-generated method stub
    return false;
  }

  public boolean[] isPermitted(PrincipalCollection arg0, String... arg1) {
    // TODO Auto-generated method stub
    return null;
  }

  public boolean[] isPermitted(PrincipalCollection arg0, List<Permission> arg1) {
    // TODO Auto-generated method stub
    return null;
  }

  public boolean isPermittedAll(PrincipalCollection arg0, String... arg1) {
    // TODO Auto-generated method stub
    return false;
  }

  public boolean isPermittedAll(PrincipalCollection arg0,
      Collection<Permission> arg1) {
    // TODO Auto-generated method stub
    return false;
  }

}
