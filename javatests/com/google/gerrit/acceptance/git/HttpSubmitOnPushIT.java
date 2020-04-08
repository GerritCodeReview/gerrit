package com.google.gerrit.acceptance.git;

import com.google.gerrit.acceptance.GitUtil;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Before;

public class HttpSubmitOnPushIT extends AbstractSubmitOnPush {

  @Before
  public void cloneProjectOverHttp() throws Exception {
    CredentialsProvider.setDefault(
        new UsernamePasswordCredentialsProvider(admin.username(), admin.httpPassword()));
    testRepo = GitUtil.cloneProject(project, admin.getHttpUrl(server) + "/a/" + project.get());
  }
}
