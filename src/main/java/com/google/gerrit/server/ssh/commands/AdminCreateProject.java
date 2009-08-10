package com.google.gerrit.server.ssh.commands;

import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.Branch;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.Project.SubmitType;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.server.GerritServer;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.ssh.AdminCommand;
import com.google.gerrit.server.ssh.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;
import com.google.inject.Inject;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.kohsuke.args4j.Option;
import org.spearce.jgit.errors.ConfigInvalidException;
import org.spearce.jgit.lib.FileBasedConfig;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.transport.SshSessionFactory;
import org.spearce.jgit.transport.URIish;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Create a new project. **/
@AdminCommand
final class AdminCreateProject extends BaseCommand {
  @Option(name = "--name", required = true, aliases = { "-n" }, usage = "name of project to be created")
  private String projectName;

  @Option(name = "--owner", aliases = { "-o" }, usage = "name of group that will own the project (defaults to: Administrators)")
  private String ownerName;

  @Option(name = "--description", aliases = { "-d" }, usage = "description of the project")
  private String projectDescription;

  @Option(name = "--submittype", aliases = { "-t" }, usage = "project submit type (F)ast forward only, (M)erge if necessary, merge (A)lways or (C)herry pick (defaults to: F)")
  private String submitTypeStr;

  @Option(name = "--usecontributoragreements", aliases = { "-ca" }, usage = "set this to true if project should make the user sign a contributor agreement   (defaults to: N)")
  private String useContributorAgreements;

  @Option(name = "--usesignedoffby", aliases = { "-so" }, usage = "set this to true if the project should mandate signed-off-by (defaults to: N)")
  private String useSignedOffBy;

  @Inject
  private ReviewDb db;

  @Inject
  private GerritServer gs;

  @Inject
  private @SitePath File sitePath;

  private AccountGroup owner = null;
  private boolean contributorAgreements = false;
  private boolean signedOffBy = false;
  private SubmitType submitType = null;

  @Override
  public void start() {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        PrintWriter p = toPrintWriter(out);

        parseCommandLine();

        try {
          validateParameters();

          Transaction txn = db.beginTransaction();

          createProject(txn);

          Repository repo  = gs.createRepository(projectName + ".git");
          repo.create(true);

          txn.commit();

          replicateToAll();
        } catch (JSchException e) {
            throw new Failure(-1, "Unable to connect to replication "
                + "server and create project");
        } catch (Exception e) {
          p.print("Error when trying to create project: "
              + e.getMessage() + "\n");
          p.flush();
        }

      }
    });
  }


  private void createProject(Transaction txn) throws OrmException,
  NoSuchEntityException {
    final Project.NameKey newProjectNameKey =
      new Project.NameKey(projectName);

    final Project newProject =
      new Project(newProjectNameKey,
      new Project.Id(db.nextProjectId()));

    newProject.setDescription(projectDescription);
    newProject.setSubmitType(submitType);
    newProject.setUseContributorAgreements(contributorAgreements);
    newProject.setUseSignedOffBy(signedOffBy);

    db.projects().insert(Collections.singleton(newProject), txn);

    final ProjectRight.Key prk =
      new ProjectRight.Key(newProjectNameKey,
          ApprovalCategory.OWN, owner.getId());
    final ProjectRight pr = new ProjectRight(prk);
    pr.setMaxValue((short) 1);
    pr.setMinValue((short) 1);
    db.projectRights().insert(Collections.singleton(pr), txn);

    final Branch newBranch =
      new Branch(
          new Branch.NameKey(newProjectNameKey, "master"));

    db.branches().insert(Collections.singleton(newBranch), txn);
  }

  private boolean stringToBoolean(final String boolStr,
      final boolean defaultValue) throws Failure {
    if (boolStr == null) {
      return defaultValue;
    }

    if (boolStr.equalsIgnoreCase("FALSE")
        || boolStr.equalsIgnoreCase("F")
        || boolStr.equalsIgnoreCase("NO")
        || boolStr.equalsIgnoreCase("N")) {
      return false;
    }

    if (boolStr.equalsIgnoreCase("TRUE")
        || boolStr.equalsIgnoreCase("T")
        || boolStr.equalsIgnoreCase("YES")
        || boolStr.equalsIgnoreCase("Y")) {
       return true;
     }

    throw new Failure(-1, "Parameter must have boolean value (true, false)");
  }

  private void validateParameters() throws Failure, OrmException {
    if (projectName.endsWith(".git")) {
      projectName = projectName.substring(0,
          projectName.length() - ".git".length());
    }

    if (ownerName == null) {
      ownerName = "Administrators";
    }

    owner = db.accountGroups().get(new AccountGroup.NameKey(ownerName));

    if (owner == null) {
      throw new Failure(-1, "Specified group does not exist");
    }

    if (projectDescription == null) {
      projectDescription = "";
    }

    contributorAgreements = stringToBoolean(useContributorAgreements, false);
    signedOffBy = stringToBoolean(useSignedOffBy, false);

    if (submitTypeStr == null) {
      submitTypeStr = "F";
    }

    switch(submitTypeStr.toUpperCase().charAt(0)) {
      case 'F': submitType = SubmitType.FAST_FORWARD_ONLY; break;
      case 'M': submitType = SubmitType.MERGE_IF_NECESSARY; break;
      case 'A': submitType = SubmitType.MERGE_ALWAYS; break;
      case 'C': submitType = SubmitType.CHERRY_PICK; break;
      default: throw new Failure(-1, "Submit type must be either: "
      		+ "(F)ast forward only, (M)erge if necessary, merge (A)lways "
      		+ "or (C)herry pick");
    }
  }

  private void replicateToAll()
  throws ConfigInvalidException, IOException, URISyntaxException,
  JSchException {
    final File cfgFile = new File(sitePath, "replication.config");
    final FileBasedConfig cfg = new FileBasedConfig(cfgFile);

    if (!cfg.getFile().exists()) {
      return;
    }

    try {
      cfg.load();

      for (final String url : allUrls(cfg)) {
        replicateProject(url);
      }
    } catch (ConfigInvalidException e) {
      throw new ConfigInvalidException("Config file " + cfg.getFile()
          + " is invalid: " + e.getMessage(), e);
    } catch (IOException e) {
      throw new IOException("Cannot read " + cfgFile + ": "
          + e.getMessage(), e);
    }
  }

  private List<String> allUrls(final FileBasedConfig cfg)
  throws ConfigInvalidException {
    List<String> names = new ArrayList<String>(cfg.getSubsections("remote"));
    List<String> urls = new ArrayList<String>();

    for (String name : names) {
      String [] urlArray = cfg.getStringList("remote", name, "url");

      for (int i = 0; i < urlArray.length; i++) {
        urls.add(urlArray[i]);
      }
    }

    return urls;
  }

  private void replicateProject(final String url) throws URISyntaxException,
  JSchException {
    SshSessionFactory sshFactory = SshSessionFactory.getInstance();
    Session sshSession;
    URIish replicateURI = new URIish(url);
    String projectPath = replicateURI.getPath();
    String scheme = replicateURI.getScheme();

    if (scheme != null && !scheme.toLowerCase().contains("ssh")) {
      return; // We can only log in to a replicate site using ssh.
    }

    if (!projectPath.contains("${name}")) {
      return; // The url must contain the project name to replicate to.
    }

    projectPath = projectPath.replaceFirst("\\$\\{name\\}",
        projectName);
    String cmd = "mkdir " + projectPath
    + "; cd " + projectPath
    + "; git init --bare";

    sshSession = sshFactory.getSession(replicateURI.getUser(),
        replicateURI.getPass(), replicateURI.getHost(), replicateURI.getPort());
    sshSession.connect();

    Channel channel = sshSession.openChannel("exec");
    ((ChannelExec) channel).setCommand(cmd);

    channel.setInputStream(null);
    ((ChannelExec) channel).setErrStream(System.err);
    channel.connect();

    while (!channel.isClosed()) {
      try {
        final int second = 1000;
        Thread.sleep(second);
      } catch (InterruptedException e) { }
    }

    channel.disconnect();
    sshSession.disconnect();
  }
}
