// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.pgm;

import static com.google.gerrit.pgm.DataSourceProvider.Type.H2;

import com.google.gerrit.reviewdb.AuthType;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Project.SubmitType;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mail.SmtpEmailSender.Encryption;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;

import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileBasedConfig;
import org.eclipse.jgit.lib.LockFile;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.util.SystemReader;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Initialize a new Gerrit installation. */
public class Init extends SiteProgram {
  @Option(name = "--batch", usage = "Batch mode; skip interactive prompting")
  private boolean batchMode;

  @Option(name = "--import-projects", usage = "Import git repositories as projects")
  private boolean importProjects;

  @Inject
  private GitRepositoryManager repositoryManager;

  @Inject
  private SchemaFactory<ReviewDb> schema;

  private boolean deleteOnFailure;
  private ConsoleUI ui;
  private Injector dbInjector;
  private Injector sysInjector;

  @Override
  public int run() throws Exception {
    ErrorLogFile.errorOnlyConsole();
    ui = ConsoleUI.getInstance(batchMode);

    try {
      upgradeFrom_Pre2_0_25();

      initSitePath();
      inject();
      initGit();
    } catch (Exception failure) {
      if (deleteOnFailure) {
        recursiveDelete(getSitePath());
      }
      throw failure;
    } catch (Error failure) {
      if (deleteOnFailure) {
        recursiveDelete(getSitePath());
      }
      throw failure;
    }
    System.err.println("Initialized " + getSitePath().getCanonicalPath());
    return 0;
  }

  private void upgradeFrom_Pre2_0_25() throws IOException {
    final File sitePath = getSitePath();

    boolean isPre2_0_25 = false;
    final String[] etcFiles =
        {"gerrit.config", "secure.config", "replication.config",
            "ssh_host_rsa_key", "ssh_host_rsa_key.pub", "ssh_host_dsa_key",
            "ssh_host_dsa_key.pub", "ssh_host_key", "contact_information.pub",
            "gitweb_config.perl", "keystore", "GerritSite.css",
            "GerritSiteFooter.html", "GerritSiteHeader.html"};
    for (String name : etcFiles) {
      if (new File(sitePath, name).exists()) {
        isPre2_0_25 = true;
        break;
      }
    }

    if (isPre2_0_25) {
      if (!ui.yesno("Upgrade '%s'", sitePath.getCanonicalPath())) {
        throw die("aborted by user");
      }

      final File etc_dir = new File(sitePath, "etc");
      if (!etc_dir.exists() && !etc_dir.mkdirs()) {
        throw die("Cannot create directory " + etc_dir);
      }
      for (String name : etcFiles) {
        final File src = new File(sitePath, name);
        final File dst = new File(etc_dir, name);
        if (src.exists()) {
          if (dst.exists()) {
            throw die("File " + src + " would overwrite " + dst);
          }
          if (!src.renameTo(dst)) {
            throw die("Cannot rename " + src + " to " + dst);
          }
        }
      }
    }
  }

  private void initSitePath() throws IOException, InterruptedException {
    final File sitePath = getSitePath();

    final File etc_dir = new File(sitePath, "etc");
    final File lib_dir = new File(sitePath, "lib");
    final File logs_dir = new File(sitePath, "logs");
    final File static_dir = new File(sitePath, "static");
    final File cache_dir = new File(sitePath, "cache");

    final File gerrit_config = new File(etc_dir, "gerrit.config");
    final File secure_config = new File(etc_dir, "secure.config");
    final File replication_config = new File(etc_dir, "replication.config");

    if (gerrit_config.exists()) {
      if (!gerrit_config.exists()) {
        throw die("'" + sitePath + "' is not a Gerrit server site");
      }
    } else if (!gerrit_config.exists()) {
      ui.header("Gerrit Code Review %s", version());
      if (!ui.yesno("Initialize '%s'", sitePath.getCanonicalPath())) {
        throw die("aborted by user");
      }

      if (!sitePath.mkdirs()) {
        throw die("Cannot make directory " + sitePath);
      }
      if (!etc_dir.mkdir()) {
        throw die("Cannot make directory " + etc_dir);
      }
      deleteOnFailure = true;

      final FileBasedConfig cfg = new FileBasedConfig(gerrit_config);
      final FileBasedConfig sec = new FileBasedConfig(secure_config);
      init_gerrit_basepath(cfg);
      init_database(cfg, sec);
      init_auth(cfg, sec);
      init_sendemail(cfg, sec);
      init_sshd(cfg, sec);
      init_httpd(cfg, sec);

      cache_dir.mkdir();
      set(cfg, "cache", "directory", cache_dir.getName());

      cfg.save();
      saveSecureConfig(sec);

      if (ui != null) {
        System.err.println();
      }
    }

    etc_dir.mkdir();
    lib_dir.mkdir();
    logs_dir.mkdir();
    static_dir.mkdir();

    if (!secure_config.exists()) {
      chmod600(secure_config);
    }
    if (!replication_config.exists()) {
      replication_config.createNewFile();
    }
  }

  private void initGit() throws OrmException, IOException {
    final File root = repositoryManager.getBasePath();
    if (root != null && importProjects) {
      System.err.println("Scanning projects under " + root);
      final ReviewDb db = schema.open();
      try {
        final HashSet<String> have = new HashSet<String>();
        for (Project p : db.projects().all()) {
          have.add(p.getName());
        }
        importProjects(root, "", db, have);
      } finally {
        db.close();
      }
    }
  }

  private void importProjects(final File dir, final String prefix,
      final ReviewDb db, final Set<String> have) throws OrmException,
      IOException {
    final File[] ls = dir.listFiles();
    if (ls == null) {
      return;
    }

    for (File f : ls) {
      if (".".equals(f.getName()) || "..".equals(f.getName())) {
      } else if (FileKey.isGitRepository(f)) {
        String name = f.getName();
        if (name.equals(".git")) {
          name = prefix.substring(0, prefix.length() - 1);
        } else if (name.endsWith(".git")) {
          name = prefix + name.substring(0, name.length() - 4);
        } else {
          name = prefix + name;
          System.err.println("Ignoring non-standard name '" + name + "'");
          continue;
        }

        if (have.contains(name)) {
          continue;
        }

        final Project.NameKey nameKey = new Project.NameKey(name);
        final Project.Id idKey = new Project.Id(db.nextProjectId());
        final Project p = new Project(nameKey, idKey);

        p.setDescription(repositoryManager.getProjectDescription(name));
        p.setSubmitType(SubmitType.MERGE_IF_NECESSARY);
        p.setUseContributorAgreements(false);
        p.setUseSignedOffBy(false);
        db.projects().insert(Collections.singleton(p));

      } else if (f.isDirectory()) {
        importProjects(f, prefix + f.getName() + "/", db, have);
      }
    }
  }

  private void saveSecureConfig(final FileBasedConfig sec) throws IOException {
    final byte[] out = Constants.encode(sec.toText());
    final File path = sec.getFile();
    final LockFile lf = new LockFile(path);
    if (!lf.lock()) {
      throw new IOException("Cannot lock " + path);
    }
    try {
      chmod600(new File(path.getParentFile(), path.getName() + ".lock"));
      lf.write(out);
      if (!lf.commit()) {
        throw new IOException("Cannot commit write to " + path);
      }
    } finally {
      lf.unlock();
    }
  }

  private static void chmod600(final File path) throws IOException {
    if (!path.exists() && !path.createNewFile()) {
      throw new IOException("Cannot create " + path);
    }
    path.setWritable(false, false /* all */);
    path.setReadable(false, false /* all */);
    path.setExecutable(false, false /* all */);

    path.setWritable(true, true /* owner only */);
    path.setReadable(true, true /* owner only */);
    if (path.isDirectory()) {
      path.setExecutable(true, true /* owner only */);
    }
  }

  private void init_gerrit_basepath(final Config cfg) {
    ui.header("Git Repositories");

    File d = new File(ui.readString("git", "Location of Git repositories"));
    set(cfg, "gerrit", "basePath", d.getPath());

    if (d.exists()) {
      if (!importProjects && d.list() != null && d.list().length > 0) {
        importProjects = ui.yesno("Import existing repositories");
      }
    } else if (!d.mkdirs()) {
      throw die("Cannot create " + d);
    }
  }

  private void init_database(final Config cfg, final Config sec) {
    ui.header("SQL Database");

    DataSourceProvider.Type db_type = ui.readEnum(H2, "Database server type");
    if (db_type == DataSourceProvider.Type.DEFAULT) {
      db_type = H2;
    }
    set(cfg, "database", "type", db_type, null);

    switch (db_type) {
      case MYSQL:
        createDownloader()
            .setRequired(true)
            .setName("MySQL Connector/J 5.1.10")
            .setJarUrl(
                "http://repo2.maven.org/maven2/mysql/mysql-connector-java/5.1.10/mysql-connector-java-5.1.10.jar")
            .setSHA1("b83574124f1a00d6f70d56ba64aa52b8e1588e6d").download();
        loadSiteLib();
        break;
    }

    final boolean userPassAuth;
    switch (db_type) {
      case H2:
        userPassAuth = false;
        new File(getSitePath(), "db").mkdirs();
        break;

      case JDBC: {
        userPassAuth = true;
        String driver = ui.readString("", "Driver class name");
        String url = ui.readString("", "url");

        set(cfg, "database", "driver", driver);
        set(cfg, "database", "url", url);
        break;
      }

      case POSTGRES:
      case POSTGRESQL:
      case MYSQL: {
        userPassAuth = true;
        String def_port = "(" + db_type.toString() + " default)";
        String hostname = ui.readString("localhost", "Server hostname");
        String port = ui.readString(def_port, "Server port");
        String database = ui.readString("reviewdb", "Database name");

        set(cfg, "database", "hostname", hostname);
        set(cfg, "database", "port", port != def_port ? port : null);
        set(cfg, "database", "database", database);
        break;
      }

      default:
        throw die("internal bug, database " + db_type + " not supported");
    }

    if (userPassAuth) {
      String user = ui.readString(username(), "Database username");
      String pass = user != null ? ui.password("%s's password", user) : null;
      set(cfg, "database", "username", user);
      set(sec, "database", "password", pass);
    }
  }

  private void init_auth(final Config cfg, final Config sec) {
    ui.header("User Authentication");

    AuthType auth_type = ui.readEnum(AuthType.OPENID, "Authentication method");
    set(cfg, "auth", "type", auth_type, null);

    switch (auth_type) {
      case HTTP:
      case HTTP_LDAP: {
        String def_hdr = "(HTTP Basic)";
        String hdr = ui.readString(def_hdr, "Username HTTP header");
        String logoutUrl = ui.readString("", "Single-sign-on logout URL");

        set(cfg, "auth", "httpHeader", hdr != def_hdr ? hdr : null);
        set(cfg, "auth", "logoutUrl", logoutUrl);
        break;
      }
    }

    switch (auth_type) {
      case LDAP:
      case HTTP_LDAP: {
        String server = ui.readString("ldap://localhost", "LDAP server");

        if (server != null && !server.startsWith("ldap://")
            && !server.startsWith("ldaps://")) {
          if (ui.yesno("Use SSL")) {
            server = "ldaps://" + server;
          } else {
            server = "ldap://" + server;
          }
        }

        final String def_dn = dnOf(server);
        String accountBase = ui.readString(def_dn, "Account BaseDN");
        String groupBase = ui.readString(accountBase, "Group BaseDN");

        String user = ui.readString(null, "LDAP username");
        String pass = user != null ? ui.password("%s's password", user) : null;

        set(cfg, "ldap", "server", server);
        set(cfg, "ldap", "username", user);
        set(sec, "ldap", "password", pass);

        set(cfg, "ldap", "accountBase", accountBase);
        set(cfg, "ldap", "groupBase", groupBase);
        break;
      }
    }
  }

  private void init_sendemail(final Config cfg, final Config sec) {
    ui.header("Email Delivery");

    String def_port = "(default)";
    String smtpserver = ui.readString("localhost", "SMTP server hostname");
    String port = ui.readString(def_port, "SMTP server port");
    Encryption enc = ui.readEnum(Encryption.NONE, "SMTP encryption");
    String username = null;
    if (enc != Encryption.NONE || !isLocal(smtpserver)) {
      username = username();
    }
    username = ui.readString(username, "SMTP username");
    String password =
        username != null ? ui.password("%s's password", username) : null;

    set(cfg, "sendemail", "smtpServer", smtpserver);
    set(cfg, "sendemail", "smtpServerPort", port != def_port ? port : null);
    set(cfg, "sendemail", "smtpEncryption", enc, Encryption.NONE);

    set(cfg, "sendemail", "smtpUser", username);
    set(sec, "sendemail", "smtpPass", password);
  }

  private void init_sshd(final Config cfg, final Config sec)
      throws IOException, InterruptedException {
    ui.header("SSH Daemon");

    String sshd_hostname = ui.readString("*", "Gerrit SSH listens on address");
    String sshd_port = ui.readString("29418", "Gerrit SSH listens on port");
    set(cfg, "sshd", "listenAddress", sshd_hostname + ":" + sshd_port);

    // Download and install BouncyCastle if the user wants to use it.
    //
    createDownloader().setRequired(false).setName("Bouncy Castle Crypto v144")
        .setJarUrl("http://www.bouncycastle.org/download/bcprov-jdk16-144.jar")
        .setSHA1("6327a5f7a3dc45e0fd735adb5d08c5a74c05c20c").download();
    loadSiteLib();

    final File etc_dir = new File(getSitePath(), "etc");
    System.err.print("Generating SSH host key ...");
    System.err.flush();
    if (SecurityUtils.isBouncyCastleRegistered()) {
      // Generate the SSH daemon host key using ssh-keygen.
      //
      final String comment = "gerrit-code-review@" + hostname();
      final File rsa = new File(etc_dir, "ssh_host_rsa_key");
      final File dsa = new File(etc_dir, "ssh_host_dsa_key");

      System.err.print(" rsa...");
      System.err.flush();
      Runtime.getRuntime().exec(new String[] {"ssh-keygen", //
          "-q" /* quiet */, //
          "-t", "rsa", //
          "-P", "", //
          "-C", comment, //
          "-f", rsa.getAbsolutePath() //
          }).waitFor();

      System.err.print(" dsa...");
      System.err.flush();
      Runtime.getRuntime().exec(new String[] {"ssh-keygen", //
          "-q" /* quiet */, //
          "-t", "dsa", //
          "-P", "", //
          "-C", comment, //
          "-f", dsa.getAbsolutePath() //
          }).waitFor();

    } else {
      // Generate the SSH daemon host key ourselves. This is complex
      // because SimpleGeneratorHostKeyProvider doesn't mark the data
      // file as only readable by us, exposing the private key for a
      // short period of time. We try to reduce that risk by creating
      // the key within a temporary directory.
      //
      final File tmpdir = new File(etc_dir, "tmp.sshkeygen");
      if (!tmpdir.mkdir()) {
        throw die("Cannot create directory " + tmpdir);
      }
      chmod600(tmpdir);

      final String keyname = "ssh_host_key";
      final File tmpkey = new File(tmpdir, keyname);
      final SimpleGeneratorHostKeyProvider p;

      System.err.print(" rsa(simple)...");
      System.err.flush();
      p = new SimpleGeneratorHostKeyProvider();
      p.setPath(tmpkey.getAbsolutePath());
      p.setAlgorithm("RSA");
      p.loadKeys(); // forces the key to generate.
      chmod600(tmpkey);

      final File key = new File(etc_dir, keyname);
      if (!tmpkey.renameTo(key)) {
        throw die("Cannot rename " + tmpkey + " to " + key);
      }
      if (!tmpdir.delete()) {
        throw die("Cannot delete " + tmpdir);
      }
    }
    System.err.println(" done");
  }

  private void init_httpd(final Config cfg, final Config sec)
      throws IOException, InterruptedException {
    ui.header("HTTP Daemon");

    final boolean reverseProxy =
        ui.yesno("Behind reverse HTTP proxy (e.g. Apache mod_proxy)");

    final boolean useSSL;
    if (reverseProxy) {
      useSSL = ui.yesno("Does the proxy server use https:// (SSL)");
    } else {
      useSSL = ui.yesno("Use https:// (SSL)");
    }

    final String scheme = useSSL ? "https" : "http";
    final String port_def = useSSL ? "8443" : "8080";
    String httpd_hostname = ui.readString(reverseProxy ? "localhost" : "*", //
        "Gerrit HTTP listens on address");
    String httpd_port = ui.readString(reverseProxy ? "8081" : port_def, //
        "Gerrit HTTP listens on port");

    String context = "/";
    if (reverseProxy) {
      context = ui.readString("/", "Gerrit's subdirectory on proxy server");
      if (!context.endsWith("/")) {
        context += "/";
      }
    }

    final String httpd_url = (reverseProxy ? "proxy-" : "") //
        + scheme + "://" + httpd_hostname + ":" + httpd_port + context;
    set(cfg, "httpd", "listenUrl", httpd_url);

    if (useSSL && !reverseProxy
        && ui.yesno("Create self-signed SSL certificate")) {
      final String certName =
          ui.readString("*".equals(httpd_hostname) ? hostname()
              : httpd_hostname, "Certificate server name");
      final String validity =
          ui.readString("365", "Certificate expires in (days)");

      final String ssl_pass = SignedToken.generateRandomKey();
      final String dname =
          "CN=" + certName + ",OU=Gerrit Code Review,O=" + domainOf(certName);

      final File etc_dir = new File(getSitePath(), "etc");
      final File tmpdir = new File(etc_dir, "tmp.sslcertgen");
      if (!tmpdir.mkdir()) {
        throw die("Cannot create directory " + tmpdir);
      }
      chmod600(tmpdir);

      final File tmpstore = new File(tmpdir, "keystore");
      Runtime.getRuntime().exec(new String[] {"keytool", //
          "-keystore", tmpstore.getAbsolutePath(), //
          "-storepass", ssl_pass, //
          "-genkeypair", //
          "-alias", certName, //
          "-keyalg", "RSA", //
          "-validity", validity, //
          "-dname", dname, //
          "-keypass", ssl_pass, //
      }).waitFor();
      chmod600(tmpstore);

      final File store = new File(etc_dir, "keystore");
      if (!tmpstore.renameTo(store)) {
        throw die("Cannot rename " + tmpstore + " to " + store);
      }
      if (!tmpdir.delete()) {
        throw die("Cannot delete " + tmpdir);
      }

      set(sec, "httpd", "sslKeyPassword", ssl_pass);
      set(cfg, "gerrit", "canonicalWebUrl", "https://" + certName + ":"
          + httpd_port + context);
    }
  }

  private <T extends Enum<?>> void set(Config cfg, String section, String name,
      T value, T def) {
    if (value != null && value != def) {
      cfg.setString(section, null, name, value.toString());
    } else {
      cfg.unset(section, null, name);
    }
  }

  private void set(Config cfg, String section, String name, String value) {
    if (value != null && !value.isEmpty()) {
      cfg.setString(section, null, name, value);
    } else {
      cfg.unset(section, null, name);
    }
  }

  private void inject() {
    dbInjector = createDbInjector();
    sysInjector = createSysInjector();
    sysInjector.injectMembers(this);
  }

  private Injector createSysInjector() {
    final List<Module> modules = new ArrayList<Module>();
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(GitRepositoryManager.class);
      }
    });
    return dbInjector.createChildInjector(modules);
  }

  private LibraryDownloader createDownloader() {
    return new LibraryDownloader(ui, getSitePath());
  }

  private static String version() {
    return com.google.gerrit.common.Version.getVersion();
  }

  private static String username() {
    return System.getProperty("user.name");
  }

  private static String hostname() {
    return SystemReader.getInstance().getHostname();
  }

  private static boolean isLocal(final String hostname) {
    try {
      return InetAddress.getByName(hostname).isLoopbackAddress();
    } catch (UnknownHostException e) {
      return false;
    }
  }

  private static String dnOf(String name) {
    if (name != null) {
      int p = name.indexOf("://");
      if (0 < p) {
        name = name.substring(p + 3);
      }

      p = name.indexOf(".");
      if (0 < p) {
        name = name.substring(p + 1);
        name = "DC=" + name.replaceAll("\\.", ",DC=");
      } else {
        name = null;
      }
    }
    return name;
  }

  private static String domainOf(String name) {
    if (name != null) {
      int p = name.indexOf("://");
      if (0 < p) {
        name = name.substring(p + 3);
      }
      p = name.indexOf(".");
      if (0 < p) {
        name = name.substring(p + 1);
      }
    }
    return name;
  }

  private static void recursiveDelete(File path) {
    File[] entries = path.listFiles();
    if (entries != null) {
      for (File e : entries) {
        recursiveDelete(e);
      }
    }
    if (!path.delete() && path.exists()) {
      System.err.println("warn: Cannot remove " + path);
    }
  }
}
