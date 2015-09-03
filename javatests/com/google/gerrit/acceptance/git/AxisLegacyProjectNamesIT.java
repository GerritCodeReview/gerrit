package com.google.gerrit.acceptance.git;

import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.deny;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.GerritServer.TestSshServerAddress;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.Inject;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import org.eclipse.jgit.lib.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@UseSsh
public class AxisLegacyProjectNamesIT extends StandaloneSiteTest {
  private String legacyProjectName;
  private Path workDir;
  private ServerContext ctx;

  @Inject private GerritApi gApi;
  @Inject private ProjectOperations projects;
  @Inject private @TestSshServerAddress InetSocketAddress sshAddress;
  @Inject private @GerritServerConfig Config config;

  @Before
  public void setUp() throws Exception {
    ctx = startServer();
    ctx.getInjector().injectMembers(this);
    workDir = sitePaths.data_dir;

    String projectName = "project/name/space/project1";
    projects.newProject().name(projectName).create();
    allowReadForAllRefs(Project.nameKey(projectName));
    legacyProjectName = projectName.replace('/', '-');
  }

  @After
  public void tearDown() throws Exception {
    if (ctx != null) {
      ctx.close();
    }
  }

  @Test
  public void legacyNameGitOverSsh() throws Exception {
    String legacySshUrl = getSshUrl(legacyProjectName);
    ImmutableMap<String, String> gitSshCommand = configureGitSsh();
    gitLsRemote(legacySshUrl, gitSshCommand);
    gitLsRemote(legacySshUrl + "/mainline", gitSshCommand);
  }

  @Test
  public void legacyNameGitOverHttp() throws Exception {
    String legacycHttpUrl = getAuthHttpUrl(legacyProjectName);
    gitLsRemote(legacycHttpUrl);
    gitLsRemote(legacycHttpUrl + "%2Fmainline");
  }

  private void gitLsRemote(String remoteUrl) throws Exception {
    gitLsRemote(remoteUrl, ImmutableMap.of());
  }

  private void gitLsRemote(String remoteUrl, ImmutableMap<String, String> env) throws Exception {
    execute(ImmutableList.<String>builder().add("git", "ls-remote", remoteUrl).build(), env);
  }

  private ImmutableMap<String, String> configureGitSsh() throws Exception {
    Path rsaKey = createAndRegisterRsaKey(admin.username());
    return ImmutableMap.<String, String>builder()
        .put(
            "GIT_SSH_COMMAND",
            "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -i" + rsaKey)
        .build();
  }

  private String getSshUrl(String projectName) {
    String legacySshUrl =
        "ssh://"
            + admin.username()
            + "@"
            + sshAddress.getHostName()
            + ":"
            + sshAddress.getPort()
            + "/"
            + projectName;
    return legacySshUrl;
  }

  private String getAuthHttpUrl(String legacyProjectName) throws Exception {
    String url = config.getString("gerrit", null, "canonicalweburl");
    String adminHttpPass = "password";
    gApi.accounts().id(admin.username()).setHttpPassword(adminHttpPass);
    String legacycHttpUrl =
        url.substring(0, 7)
            + admin.username()
            + ":"
            + adminHttpPass
            + "@"
            + url.substring(7, url.length())
            + "/a/"
            + legacyProjectName;
    return legacycHttpUrl;
  }

  private void allowReadForAllRefs(Project.NameKey project) {
    projects
        .project(project)
        .forUpdate()
        .add(deny(Permission.READ).ref("refs/*").group(SystemGroupBackend.ANONYMOUS_USERS))
        .update();
  }

  private Path createAndRegisterRsaKey(String username) throws Exception {
    String keyName = String.format("id_rsa_%s", username);
    Path rsaKey = workDir.resolve(keyName);
    execute(
        ImmutableList.<String>builder()
            .add("ssh-keygen")
            .add("-t")
            .add("rsa")
            .add("-q")
            .add("-f")
            .add(rsaKey.toString())
            .add("-P")
            .add("")
            .build());

    gApi.accounts()
        .id(username)
        .addSshKey(
            new String(
                java.nio.file.Files.readAllBytes(rsaKey.getParent().resolve(keyName + ".pub")),
                UTF_8));
    return rsaKey;
  }

  private String execute(ImmutableList<String> cmd) throws Exception {
    return execute(cmd, ImmutableMap.of());
  }

  private String execute(ImmutableList<String> cmd, ImmutableMap<String, String> env)
      throws Exception {
    return execute(cmd, workDir.toFile(), env);
  }
}
