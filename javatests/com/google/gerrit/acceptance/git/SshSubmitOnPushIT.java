package com.google.gerrit.acceptance.git;

import com.google.gerrit.acceptance.NoHttpd;
import org.junit.Before;

@NoHttpd
public class SshSubmitOnPushIT extends AbstractSubmitOnPush {

  @Before
  public void cloneProjectOverSsh() throws Exception {
    testRepo = cloneProject(project, admin);
  }
}
