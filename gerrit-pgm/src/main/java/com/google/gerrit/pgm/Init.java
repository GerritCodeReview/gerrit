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
import static org.eclipse.jgit.util.StringUtils.equalsIgnoreCase;

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

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileBasedConfig;
import org.eclipse.jgit.lib.LockFile;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.util.SystemReader;
import org.kohsuke.args4j.Option;

import java.io.Console;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
  private Console console;
  private Injector dbInjector;
  private Injector sysInjector;

  @Override
  public int run() throws Exception {
    console = !batchMode ? System.console() : null;
    try {
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

  private void initSitePath() throws IOException, InterruptedException,
      NoSuchAlgorithmException {
    final File sitePath = getSitePath();

    final File gerrit_config = new File(sitePath, "gerrit.config");
    final File secure_config = new File(sitePath, "secure.config");
    final File replication_config = new File(sitePath, "replication.config");
    final File lib_dir = new File(sitePath, "lib");
    final File logs_dir = new File(sitePath, "logs");
    final File static_dir = new File(sitePath, "static");
    final File cache_dir = new File(sitePath, "cache");

    if (gerrit_config.exists()) {
      if (!gerrit_config.exists()) {
        throw die("'" + sitePath + "' is not a Gerrit server site");
      }
    } else if (!gerrit_config.exists()) {
      if (console != null) {
        console.printf("Gerrit Code Review %s\n",
            com.google.gerrit.common.Version.getVersion());
      }
      needyes("Initialize '%s'", sitePath.getCanonicalPath());

      if (!sitePath.mkdirs()) {
        throw die("Cannot make directory " + sitePath);
      }
      deleteOnFailure = true;

      final FileBasedConfig cfg = new FileBasedConfig(gerrit_config);
      final FileBasedConfig sec = new FileBasedConfig(secure_config);
      init_gerrit_basepath(cfg);
      init_database(cfg, sec);
      init_auth(cfg, sec);
      init_sendemail(cfg, sec);
      init_daemons(cfg, sec);

      cache_dir.mkdir();
      set(cfg, "cache", "directory", cache_dir.getName());

      cfg.save();
      saveSecureConfig(sec);

      if (console != null) {
        System.err.println();
      }
    }

    if (!secure_config.exists()) {
      secure(secure_config);
    }
    if (!replication_config.exists()) {
      replication_config.createNewFile();
    }

    lib_dir.mkdir();
    logs_dir.mkdir();
    static_dir.mkdir();
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
      secure(new File(path.getParentFile(), path.getName() + ".lock"));
      lf.write(out);
      if (!lf.commit()) {
        throw new IOException("Cannot commit write to " + path);
      }
    } finally {
      lf.unlock();
    }
  }

  private void secure(final File path) throws IOException {
    if (!path.exists() && !path.createNewFile()) {
      throw new IOException("Cannot create " + path);
    }
    path.setWritable(false, false /* all */);
    path.setReadable(false, false /* all */);
    path.setExecutable(false, false /* all */);

    path.setWritable(true, true /* owner only */);
    path.setReadable(true, true /* owner only */);
  }

  private void init_gerrit_basepath(final Config cfg) {
    header("Git Repositories");

    File git_dir = new File(askdefault("git", "Location of Git repositories"));
    set(cfg, "gerrit", "basePath", git_dir.getPath());

    if (git_dir.exists()) {
      if (!importProjects && git_dir.list() != null
          && git_dir.list().length > 0) {
        importProjects = yesno("Import existing repositories");
      }
    } else if (!git_dir.mkdirs()) {
      throw die("Cannot create " + git_dir);
    }
  }

  private void init_database(final Config cfg, final Config sec)
      throws NoSuchAlgorithmException {
    header("SQL Database");

    final String D = "database";
    DataSourceProvider.Type db_type = askdefault(H2, "Database server type");
    if (db_type == DataSourceProvider.Type.DEFAULT) {
      db_type = H2;
    }
    set(cfg, D, "type", db_type, null);

    switch (db_type) {
      case MYSQL:
        downloadLibraryJAR(true /* required */,
            "MySQL Connector/J 5.1.10", //
            "b83574124f1a00d6f70d56ba64aa52b8e1588e6d", //
            "http://repo2.maven.org/maven2/mysql/mysql-connector-java/5.1.10/mysql-connector-java-5.1.10.jar");
        break;
    }

    switch (db_type) {
      case H2:
        new File(getSitePath(), "db").mkdirs();
        break;

      case JDBC: {
        String driver = askdefault("", "Driver class name");
        String url = askdefault("", "url");
        String username = askdefault(username(), "Application username");
        String password =
            username != null ? password("%s's password", username) : null;

        set(cfg, D, "driver", driver);
        set(cfg, D, "url", url);
        set(cfg, D, "username", username);
        set(sec, D, "password", password);
        break;
      }

      case POSTGRES:
      case POSTGRESQL:
      case MYSQL: {
        String def_port = "(" + db_type.toString() + " default)";
        String hostname = askdefault("localhost", "Server hostname");
        String port = askdefault(def_port, "Server port");
        String database = askdefault("reviewdb", "Database name");
        String username = askdefault(username(), "Application username");
        String password =
            username != null ? password("%s's password", username) : null;

        set(cfg, D, "hostname", hostname);
        set(cfg, D, "port", port != def_port ? port : null);
        set(cfg, D, "database", database);

        set(cfg, D, "username", username);
        set(sec, D, "password", password);
        break;
      }
    }
  }

  private void downloadLibraryJAR(final boolean req, final String name,
      final String sha1, final String jarUrl) throws NoSuchAlgorithmException {
    final String jarName = jarUrl.substring(jarUrl.lastIndexOf('/') + 1);
    final File dst = new File(new File(getSitePath(), "lib"), jarName);

    if (dst.exists()) {
      return;
    }

    StringBuilder msg = new StringBuilder();
    msg.append("\n");
    msg.append("Gerrit Code Review is not shipped with %s\n");
    if (req) {
      msg.append("**  This library is required for your configuration. **\n");
    } else {
      msg.append("  If available, Gerrit can take advantage of features\n");
      msg.append("  in the library, but will also function without it.\n");
    }
    msg.append("Download and install it now");

    if (!yesno(msg.toString(), name)) {
      return;
    }
    if (!dst.getParentFile().exists() && !dst.getParentFile().mkdirs()) {
      die("Cannot create " + dst.getParentFile());
    }

    // TODO: We really should also support proxy servers here.
    //
    System.err.print("Downloading " + jarUrl + " ...");
    System.err.flush();
    try {
      final MessageDigest md = MessageDigest.getInstance("SHA-1");
      final InputStream in = new URL(jarUrl).openStream();
      try {
        final OutputStream out = new FileOutputStream(dst);
        try {
          final byte[] buf = new byte[8192];
          int n;
          while ((n = in.read(buf)) > 0) {
            md.update(buf, 0, n);
            out.write(buf, 0, n);
          }
        } finally {
          out.close();
        }
      } finally {
        in.close();
      }

      if (!sha1.equals(ObjectId.fromRaw(md.digest()).name())) {
        throw new IOException("SHA-1 checksum mismatch");
      }
      System.err.println(" OK");
    } catch (IOException e) {
      dst.delete();
      System.err.println();
      System.err.println();
      System.err.println("error: " + e.getMessage());
      System.err.println("Please download:");
      System.err.println();
      System.err.println("  " + jarUrl);
      System.err.println();
      System.err.println("into the directory:");
      System.err.println();
      System.err.println("  " + dst.getParentFile());
      System.err.println("  " + sha1 + "\t" + name);
      System.err.println();

      if (!yesno("Continue without this library")) {
        throw die("aborted by user");
      }
    }
  }

  private void init_auth(final Config cfg, final Config sec) {
    header("User Authentication");

    final String A = "auth";
    AuthType auth_type = askdefault(AuthType.OPENID, "Authentication method");
    set(cfg, A, "type", auth_type, null);

    switch (auth_type) {
      case HTTP:
      case HTTP_LDAP: {
        String def_hdr = "(HTTP Basic)";
        String hdr = askdefault(def_hdr, "Username HTTP header");
        String logoutUrl = askdefault("", "Single-sign-on logout URL");

        set(cfg, A, "httpHeader", hdr != def_hdr ? hdr : null);
        set(cfg, A, "logoutUrl", logoutUrl);
        break;
      }
    }

    switch (auth_type) {
      case LDAP:
      case HTTP_LDAP: {
        final String D = "ldap";
        String server = askdefault("ldap://localhost", "LDAP server");

        if (server != null && !server.startsWith("ldap://")
            && !server.startsWith("ldaps://")) {
          if (yesno("Use SSL")) {
            server = "ldaps://" + server;
          } else {
            server = "ldap://" + server;
          }
        }

        String def_dn = server;
        if (def_dn != null) {
          if (0 < def_dn.indexOf("://")) {
            def_dn = def_dn.substring(def_dn.indexOf("://") + 3);
          }
          if (0 < def_dn.indexOf(".")) {
            def_dn = def_dn.substring(def_dn.indexOf(".") + 1);
            def_dn = "DC=" + def_dn.replaceAll("\\.", ",DC=");
          } else {
            def_dn = null;
          }
        }

        String accountBase = askdefault(def_dn, "Account BaseDN");
        String groupBase = askdefault(accountBase, "Group BaseDN");

        String username = askdefault(null, "Application username");
        String password =
            username != null ? password("%s's password", username) : null;

        set(cfg, D, "server", server);
        set(cfg, D, "username", username);
        set(sec, D, "password", password);

        set(cfg, D, "accountBase", accountBase);
        set(cfg, D, "groupBase", groupBase);
        break;
      }
    }
  }

  private void init_sendemail(final Config cfg, final Config sec) {
    header("Email Delivery");

    final String D = "sendemail";

    String def_port = "(default)";
    String smtpserver = askdefault("localhost", "SMTP server hostname");
    String port = askdefault(def_port, "SMTP server port");
    Encryption enc = askdefault(Encryption.NONE, "SMTP encryption");
    String username = null;
    if ((!"localhost".equals(smtpserver) && !"127.0.0.1".equals(smtpserver))
        || enc != Encryption.NONE) {
      username = username();
    }
    username = askdefault(username, "Application username");
    String password =
        username != null ? password("%s's password", username) : null;

    set(cfg, D, "smtpServer", smtpserver);
    set(cfg, D, "smtpServerPort", port != def_port ? port : null);
    set(cfg, D, "smtpEncryption", enc, Encryption.NONE);

    set(cfg, D, "smtpUser", username);
    set(sec, D, "smtpPass", password);
  }

  private void init_daemons(final Config cfg, final Config sec)
      throws IOException, InterruptedException, NoSuchAlgorithmException {
    header("Internal Daemons (HTTP, SSH)");

    String sshd_hostname = askdefault("*", "Gerrit SSH listens on address");
    String sshd_port = askdefault("29418", "Gerrit SSH listens on port");
    set(cfg, "sshd", "listenAddress", sshd_hostname + ":" + sshd_port);

    downloadLibraryJAR(false /* optional (not required) */,
        "Bouncy Castle Crypto v144", //
        "6327a5f7a3dc45e0fd735adb5d08c5a74c05c20c", //
        "http://www.bouncycastle.org/download/bcprov-jdk16-144.jar");

    final boolean behindProxy =
        yesno("Behind reverse HTTP proxy (e.g. Apache mod_proxy)");

    final boolean useSSL;
    if (behindProxy) {
      useSSL = yesno("Does the proxy server use https:// (SSL)");
    } else {
      useSSL = yesno("Use https:// (SSL)");
    }

    final String scheme = useSSL ? "https" : "http";
    String httpd_hostname =
        askdefault(behindProxy ? "localhost" : "*",
            "Gerrit HTTP listens on address");
    String port_def = useSSL ? "8443" : "8080";
    String httpd_port =
        askdefault(behindProxy ? "8081" : port_def,
            "Gerrit HTTP listens on port");

    String context = "/";
    if (behindProxy) {
      context = askdefault("/", "Gerrit's subdirectory on proxy server");
      if (!context.endsWith("/")) {
        context += "/";
      }
    }
    final String httpd_url = (behindProxy ? "proxy-" : "") //
        + scheme + "://" + httpd_hostname + ":" + httpd_port + context;
    set(cfg, "httpd", "listenUrl", httpd_url);

    if (!behindProxy && useSSL) {
      if (yesno("Create self-signed SSL certificate")) {
        if ("*".equals(httpd_hostname)) {
          httpd_hostname =
              askdefault(SystemReader.getInstance().getHostname(),
                  "Server name (for certificate)");
        }
        final File store = new File(getSitePath(), "keystore");
        String ssl_pass = SignedToken.generateRandomKey();
        Runtime.getRuntime().exec(new String[] {"keytool", //
            "-keystore", store.getAbsolutePath(), //
            "-storepass", ssl_pass, //
            "-genkeypair", //
            "-alias", "gerrit_httpd", //
            "-keyalg", "RSA", //
            "-validity", "365", // days
            "-dname", "CN=" + httpd_hostname + ",O=self", //
            "-keypass", ssl_pass, //
        }).waitFor();
        secure(store);
        set(sec, "httpd", "sslKeyPassword", ssl_pass);
        set(cfg, "gerrit", "canonicalWebUrl", "https://" + httpd_hostname + ":"
            + httpd_port + context);
      }
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

  private void needyes(String msg, Object... args) {
    if (!yesno(msg, args)) {
      throw die("aborted by user");
    }
  }

  private boolean yesno(String fmt, Object... args) {
    return yesno(true, fmt, args);
  }

  private boolean yesno(boolean def, String fmt, Object... args) {
    if (console == null) {
      return def;
    }

    final String prompt = String.format(fmt, args);
    for (;;) {
      final String yn = console.readLine("%-30s [y/n]? ", prompt);
      if (yn == null) {
        throw die("aborted by user");
      }
      if (yn.equalsIgnoreCase("y") || yn.equalsIgnoreCase("yes")) {
        return true;
      }
      if (yn.equalsIgnoreCase("n") || yn.equalsIgnoreCase("no")) {
        return false;
      }
    }
  }

  private String askdefault(String def, String fmt, Object... args) {
    if (def != null && def.isEmpty()) {
      def = null;
    }
    if (console == null) {
      return def;
    }

    final String prompt = String.format(fmt, args);
    String r;
    if (def != null) {
      r = console.readLine("%-30s [%s]: ", prompt, def);
    } else {
      r = console.readLine("%-30s : ", prompt);
    }
    if (r == null) {
      throw die("aborted by user");
    }
    r = r.trim();
    if (r.isEmpty()) {
      return def;
    }
    return r;
  }

  private String password(String fmt, Object... args) {
    if (console == null) {
      return null;
    }

    final String prompt = String.format(fmt, args);
    for (;;) {
      char[] firstArray = console.readPassword("%-30s : ", prompt);
      if (firstArray == null) {
        throw die("aborted by user");
      }
      char[] secondArray = console.readPassword("%30s : ", "confirm password");
      if (secondArray == null) {
        throw die("aborted by user");
      }

      String first = new String(firstArray).trim();
      String second = new String(secondArray).trim();
      if (!first.equals(second)) {
        console.printf("error: Passwords did not match; try again\n");
        continue;
      }
      return !first.isEmpty() ? first : null;
    }
  }

  private <T extends Enum<?>> T askdefault(T def, String fmt, Object... args) {
    if (console == null) {
      return def;
    }

    final String prompt = String.format(fmt, args);
    final T[] options = all(def);
    for (;;) {
      String r = console.readLine("%-30s [%s/?]: ", prompt, def.toString());
      if (r == null) {
        throw die("aborted by user");
      }
      r = r.trim();
      if (r.isEmpty()) {
        return def;
      }
      for (final T e : options) {
        if (equalsIgnoreCase(e.toString(), r)) {
          return e;
        }
      }
      if (!"?".equals(r)) {
        console.printf("error: '%s' is not a valid choice\n", r);
      }
      console.printf("       Supported options are:\n");
      for (final T e : options) {
        console.printf("         %s\n", e.toString().toLowerCase());
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static <T extends Enum<?>> T[] all(final T defaultValue) {
    try {
      return (T[]) defaultValue.getClass().getMethod("values").invoke(null);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    } catch (SecurityException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    } catch (InvocationTargetException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    }
  }

  private void header(String fmt, Object... args) {
    if (console != null) {
      console.printf("\n*** " + fmt + "\n*** \n\n", args);
    }
  }

  private String username() {
    return System.getProperty("user.name");
  }

  private void recursiveDelete(File dir) {
    File[] entries = dir.listFiles();
    if (entries != null) {
      for (File e : entries) {
        recursiveDelete(e);
      }
    }
    dir.delete();
  }
}
