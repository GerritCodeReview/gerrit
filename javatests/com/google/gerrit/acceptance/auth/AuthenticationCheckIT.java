package com.google.gerrit.acceptance.auth;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import org.junit.Test;

public class AuthenticationCheckIT extends AbstractDaemonTest {
  @Test
  public void authCheck_loggedInUser_returnsOk() throws Exception {
    RestResponse r = adminRestSession.get("/auth-check");
    r.assertNoContent();
  }

  @Test
  public void authCheck_anonymousUser_returnsForbidden() throws Exception {
    RestSession anonymous = new RestSession(server, null);
    RestResponse r = anonymous.get("/auth-check");
    r.assertForbidden();
  }
}
