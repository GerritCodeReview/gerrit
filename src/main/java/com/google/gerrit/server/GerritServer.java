// Copyright 2008 Google Inc.
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

package com.google.gerrit.server;

import com.google.gerrit.client.data.AccountCache;
import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.data.GerritConfig;
import com.google.gerrit.client.data.GitwebLink;
import com.google.gerrit.client.data.GroupCache;
import com.google.gerrit.client.data.ProjectCache;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.SchemaVersion;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.workflow.NoOpFunction;
import com.google.gerrit.client.workflow.SubmitFunction;
import com.google.gerrit.git.RepositoryCache;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;
import com.google.gwtorm.jdbc.Database;
import com.google.gwtorm.jdbc.SimpleDataSource;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spearce.jgit.lib.PersonIdent;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/** Global server-side state for Gerrit. */
public class GerritServer {
  private static final Logger log = LoggerFactory.getLogger(GerritServer.class);
  private static DataSource datasource;
  private static GerritServer impl;

  static void closeDataSource() {
    if (datasource != null) {
      try {
        try {
          Class.forName("com.mchange.v2.c3p0.DataSources").getMethod("destroy",
              DataSource.class).invoke(null, datasource);
        } catch (Throwable bad) {
          // Oh well, its not a c3p0 pooled connection. Too bad its
          // not standardized how "good applications cleanup".
        }
      } finally {
        datasource = null;
      }
    }
  }

  /**
   * Obtain the singleton server instance for this web application.
   * 
   * @return the server instance. Never null.
   * @throws OrmException the database could not be configured. There is
   *         something wrong with the schema configuration in {@link ReviewDb}
   *         that must be addressed by a developer.
   * @throws XsrfException the XSRF support could not be correctly configured to
   *         protect the application against cross-site request forgery. The JVM
   *         is most likely lacking critical security algorithms.
   */
  public static synchronized GerritServer getInstance() throws OrmException,
      XsrfException {
    if (impl == null) {
      try {
        impl = new GerritServer();
      } catch (OrmException e) {
        closeDataSource();
        log.error("GerritServer ORM is unavailable", e);
        throw e;
      } catch (XsrfException e) {
        closeDataSource();
        log.error("GerritServer XSRF support failed to initailize", e);
        throw e;
      }
    }
    return impl;
  }

  private final Database<ReviewDb> db;
  private SystemConfig sConfig;
  private final PersonIdent gerritPersonIdentTemplate;
  private final SignedToken xsrf;
  private final SignedToken account;
  private final SignedToken emailReg;
  private final RepositoryCache repositories;
  private final javax.mail.Session outgoingMail;

  private GerritServer() throws OrmException, XsrfException {
    db = createDatabase();
    loadSystemConfig();
    if (sConfig == null) {
      throw new OrmException("No " + SystemConfig.class.getName() + " found");
    }

    xsrf = new SignedToken(sConfig.maxSessionAge, sConfig.xsrfPrivateKey);

    final int accountCookieAge;
    switch (sConfig.getLoginType()) {
      case HTTP:
        accountCookieAge = -1; // expire when the browser closes
        break;
      case OPENID:
      default:
        accountCookieAge = sConfig.maxSessionAge;
        break;
    }
    account = new SignedToken(accountCookieAge, sConfig.accountPrivateKey);
    emailReg = new SignedToken(5 * 24 * 60 * 60, sConfig.accountPrivateKey);

    if (sConfig.gitBasePath != null) {
      repositories = new RepositoryCache(new File(sConfig.gitBasePath));
    } else {
      repositories = null;
    }

    String email = sConfig.gerritGitEmail;
    if (email == null || email.length() == 0) {
      try {
        email = "gerrit@" + InetAddress.getLocalHost().getCanonicalHostName();
      } catch (UnknownHostException e) {
        email = "gerrit@localhost";
      }
    }
    gerritPersonIdentTemplate = new PersonIdent(sConfig.gerritGitName, email);
    outgoingMail = createOutgoingMail();

    Common.setSchemaFactory(db);
    Common.setProjectCache(new ProjectCache());
    Common.setAccountCache(new AccountCache());
    Common.setGroupCache(new GroupCache(sConfig));
  }

  private Database<ReviewDb> createDatabase() throws OrmException {
    final String dsName = "java:comp/env/jdbc/ReviewDb";
    try {
      datasource = (DataSource) new InitialContext().lookup(dsName);
    } catch (NamingException namingErr) {
      final Properties p = readGerritDataSource();
      if (p == null) {
        throw new OrmException("Initialization error:\n" + "  * No DataSource "
            + dsName + "\n" + "  * No -DGerritServer=GerritServer.properties"
            + " on Java command line", namingErr);
      }

      try {
        datasource = new SimpleDataSource(p);
      } catch (SQLException se) {
        throw new OrmException("Database unavailable", se);
      }
    }
    return new Database<ReviewDb>(datasource, ReviewDb.class);
  }

  private Properties readGerritDataSource() throws OrmException {
    final Properties srvprop = new Properties();
    String name = System.getProperty("GerritServer");
    if (name == null) {
      name = "GerritServer.properties";
    }
    try {
      final InputStream in = new FileInputStream(name);
      try {
        srvprop.load(in);
      } finally {
        in.close();
      }
    } catch (IOException e) {
      throw new OrmException("Cannot read " + name, e);
    }

    final Properties dbprop = new Properties();
    for (final Map.Entry<Object, Object> e : srvprop.entrySet()) {
      final String key = (String) e.getKey();
      if (key.startsWith("database.")) {
        dbprop.put(key.substring("database.".length()), e.getValue());
      }
    }
    return dbprop;
  }

  private void initSystemConfig(final ReviewDb c) throws OrmException {
    final AccountGroup admin =
        new AccountGroup(new AccountGroup.NameKey("Administrators"),
            new AccountGroup.Id(c.nextAccountGroupId()));
    admin.setDescription("Gerrit Site Administrators");
    c.accountGroups().insert(Collections.singleton(admin));

    final AccountGroup anonymous =
        new AccountGroup(new AccountGroup.NameKey("Anonymous Users"),
            new AccountGroup.Id(c.nextAccountGroupId()));
    anonymous.setDescription("Any user, signed-in or not");
    anonymous.setOwnerGroupId(admin.getId());
    c.accountGroups().insert(Collections.singleton(anonymous));

    final AccountGroup registered =
        new AccountGroup(new AccountGroup.NameKey("Registered Users"),
            new AccountGroup.Id(c.nextAccountGroupId()));
    registered.setDescription("Any signed-in user");
    registered.setOwnerGroupId(admin.getId());
    c.accountGroups().insert(Collections.singleton(registered));

    final SystemConfig s = SystemConfig.create();
    s.maxSessionAge = 12 * 60 * 60 /* seconds */;
    s.xsrfPrivateKey = SignedToken.generateRandomKey();
    s.accountPrivateKey = SignedToken.generateRandomKey();
    s.sshdPort = 29418;
    s.adminGroupId = admin.getId();
    s.anonymousGroupId = anonymous.getId();
    s.registeredGroupId = registered.getId();
    s.gerritGitName = "Gerrit Code Review";
    s.setLoginType(SystemConfig.LoginType.OPENID);
    c.systemConfig().insert(Collections.singleton(s));
  }

  private void initWildCardProject(final ReviewDb c) throws OrmException {
    final Project proj;

    proj =
        new Project(new Project.NameKey("-- All Projects --"),
            ProjectRight.WILD_PROJECT);
    proj.setDescription("Rights inherited by all other projects");
    proj.setOwnerGroupId(sConfig.adminGroupId);
    proj.setUseContributorAgreements(false);
    c.projects().insert(Collections.singleton(proj));
  }

  private void initVerifiedCategory(final ReviewDb c) throws OrmException {
    final Transaction txn = c.beginTransaction();
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(new ApprovalCategory.Id("VRIF"), "Verified");
    cat.setPosition((short) 0);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 1, "Verified"));
    vals.add(value(cat, 0, "No score"));
    vals.add(value(cat, -1, "Fails"));
    c.approvalCategories().insert(Collections.singleton(cat), txn);
    c.approvalCategoryValues().insert(vals, txn);
    txn.commit();
  }

  private void initCodeReviewCategory(final ReviewDb c) throws OrmException {
    final Transaction txn = c.beginTransaction();
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(new ApprovalCategory.Id("CRVW"), "Code Review");
    cat.setPosition((short) 1);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 2, "Looks good to me, approved"));
    vals.add(value(cat, 1, "Looks good to me, but someone else must approve"));
    vals.add(value(cat, 0, "No score"));
    vals.add(value(cat, -1, "I would prefer that you didn't submit this"));
    vals.add(value(cat, -2, "Do not submit"));
    c.approvalCategories().insert(Collections.singleton(cat), txn);
    c.approvalCategoryValues().insert(vals, txn);
    txn.commit();

    final ProjectRight approve =
        new ProjectRight(new ProjectRight.Key(ProjectRight.WILD_PROJECT, cat
            .getId(), sConfig.registeredGroupId));
    approve.setMaxValue((short) 1);
    approve.setMinValue((short) -1);
    c.projectRights().insert(Collections.singleton(approve));
  }

  private void initReadCategory(final ReviewDb c) throws OrmException {
    final Transaction txn = c.beginTransaction();
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(ApprovalCategory.READ, "Read Access");
    cat.setPosition((short) -1);
    cat.setFunctionName(NoOpFunction.NAME);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 1, "Read access"));
    vals.add(value(cat, -1, "No access"));
    c.approvalCategories().insert(Collections.singleton(cat), txn);
    c.approvalCategoryValues().insert(vals, txn);
    txn.commit();
    {
      final ProjectRight read =
          new ProjectRight(new ProjectRight.Key(ProjectRight.WILD_PROJECT, cat
              .getId(), sConfig.anonymousGroupId));
      read.setMaxValue((short) 1);
      read.setMinValue((short) 0);
      c.projectRights().insert(Collections.singleton(read));
    }
    {
      final ProjectRight read =
          new ProjectRight(new ProjectRight.Key(ProjectRight.WILD_PROJECT, cat
              .getId(), sConfig.adminGroupId));
      read.setMaxValue((short) 1);
      read.setMinValue((short) 0);
      c.projectRights().insert(Collections.singleton(read));
    }
  }

  private void initSubmitCategory(final ReviewDb c) throws OrmException {
    final Transaction txn = c.beginTransaction();
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(ApprovalCategory.SUBMIT, "Submit");
    cat.setPosition((short) -1);
    cat.setFunctionName(SubmitFunction.NAME);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 1, "Submit"));
    c.approvalCategories().insert(Collections.singleton(cat), txn);
    c.approvalCategoryValues().insert(vals, txn);
    txn.commit();
  }

  private void initPushTagCategory(final ReviewDb c) throws OrmException {
    final Transaction txn = c.beginTransaction();
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(ApprovalCategory.PUSH_TAG, "Push Annotated Tag");
    cat.setPosition((short) -1);
    cat.setFunctionName(NoOpFunction.NAME);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 1, "Create Tag"));
    c.approvalCategories().insert(Collections.singleton(cat), txn);
    c.approvalCategoryValues().insert(vals, txn);
    txn.commit();
  }

  private void initPushUpdateBranchCategory(final ReviewDb c)
      throws OrmException {
    final Transaction txn = c.beginTransaction();
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(ApprovalCategory.PUSH_HEAD, "Push Branch");
    cat.setPosition((short) -1);
    cat.setFunctionName(NoOpFunction.NAME);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, ApprovalCategory.PUSH_HEAD_UPDATE, "Update Branch"));
    vals.add(value(cat, ApprovalCategory.PUSH_HEAD_CREATE, "Create Branch"));
    vals.add(value(cat, ApprovalCategory.PUSH_HEAD_REPLACE,
        "Force Push Branch; Delete Branch"));
    c.approvalCategories().insert(Collections.singleton(cat), txn);
    c.approvalCategoryValues().insert(vals, txn);
    txn.commit();
  }

  private static ApprovalCategoryValue value(final ApprovalCategory cat,
      final int value, final String name) {
    return new ApprovalCategoryValue(new ApprovalCategoryValue.Id(cat.getId(),
        (short) value), name);
  }

  private void loadSystemConfig() throws OrmException {
    final ReviewDb c = db.open();
    try {
      SchemaVersion sVer;
      try {
        sVer = c.schemaVersion().get(new SchemaVersion.Key());
      } catch (OrmException e) {
        // Assume the schema doesn't exist.
        //
        sVer = null;
      }

      if (sVer == null) {
        // Assume the schema is empty and populate it.
        //
        c.createSchema();
        sVer = SchemaVersion.create();
        sVer.versionNbr = ReviewDb.VERSION;
        c.schemaVersion().insert(Collections.singleton(sVer));

        initSystemConfig(c);
        sConfig = c.systemConfig().get(new SystemConfig.Key());
        initWildCardProject(c);
        initReadCategory(c);
        initVerifiedCategory(c);
        initCodeReviewCategory(c);
        initSubmitCategory(c);
        initPushTagCategory(c);
        initPushUpdateBranchCategory(c);
      }

      if (sVer.versionNbr == 2) {
        initPushTagCategory(c);
        initPushUpdateBranchCategory(c);

        sVer.versionNbr = 3;
        c.schemaVersion().update(Collections.singleton(sVer));
      }

      if (sVer.versionNbr == ReviewDb.VERSION) {
        sConfig = c.systemConfig().get(new SystemConfig.Key());

      } else {
        throw new OrmException("Unsupported schema version " + sVer.versionNbr);
      }

      loadGerritConfig(c);
    } finally {
      c.close();
    }
  }

  private void loadGerritConfig(final ReviewDb db) throws OrmException {
    final GerritConfig r = new GerritConfig();
    r.setCanonicalUrl(getCanonicalURL());
    r.setSshdPort(sConfig.sshdPort);
    r.setUseContributorAgreements(sConfig.useContributorAgreements);
    r.setGitDaemonUrl(sConfig.gitDaemonUrl);
    r.setUseRepoDownload(sConfig.useRepoDownload);
    r.setLoginType(sConfig.getLoginType());
    if (sConfig.gitwebUrl != null) {
      r.setGitwebLink(new GitwebLink(sConfig.gitwebUrl));
    }

    for (final ApprovalCategory c : db.approvalCategories().all()) {
      r.add(new ApprovalType(c, db.approvalCategoryValues().byCategory(
          c.getId()).toList()));
    }

    Common.setGerritConfig(r);
  }

  private javax.mail.Session createOutgoingMail() {
    final String dsName = "java:comp/env/mail/Outgoing";
    try {
      return (javax.mail.Session) new InitialContext().lookup(dsName);
    } catch (NamingException namingErr) {
      return null;
    }
  }

  /** Time (in seconds) that user sessions stay "signed in". */
  public int getSessionAge() {
    return sConfig.maxSessionAge;
  }

  /** Get the signature support used to protect against XSRF attacks. */
  public SignedToken getXsrfToken() {
    return xsrf;
  }

  /** Get the signature support used to protect user identity cookies. */
  public SignedToken getAccountToken() {
    return account;
  }

  /** Get the signature used for email registration/validation links. */
  public SignedToken getEmailRegistrationToken() {
    return emailReg;
  }

  public String getLoginHttpHeader() {
    return sConfig.loginHttpHeader;
  }

  public String getEmailFormat() {
    return sConfig.emailFormat;
  }

  /** A binary string key to encrypt cookies related to account data. */
  public String getAccountCookieKey() {
    byte[] r = new byte[sConfig.accountPrivateKey.length()];
    for (int k = r.length - 1; k >= 0; k--) {
      r[k] = (byte) sConfig.accountPrivateKey.charAt(k);
    }
    r = Base64.decodeBase64(r);
    final StringBuilder b = new StringBuilder();
    for (int i = 0; i < r.length; i++) {
      b.append((char) r[i]);
    }
    return b.toString();
  }

  /** Local filesystem location of header/footer/CSS configuration files. */
  public File getSitePath() {
    return sConfig.sitePath != null ? new File(sConfig.sitePath) : null;
  }

  /** Optional canonical URL for this application. */
  public String getCanonicalURL() {
    String u = sConfig.canonicalUrl;
    if (u != null && !u.endsWith("/")) {
      u += "/";
    }
    return u;
  }

  /** Get the repositories maintained by this server. */
  public RepositoryCache getRepositoryCache() {
    return repositories;
  }

  /** The mail session used to send messages; null if not configured. */
  public javax.mail.Session getOutgoingMail() {
    return outgoingMail;
  }

  /** Get a new identity representing this Gerrit server in Git. */
  public PersonIdent newGerritPersonIdent() {
    return new PersonIdent(gerritPersonIdentTemplate);
  }

  public boolean isAllowGoogleAccountUpgrade() {
    return sConfig.allowGoogleAccountUpgrade;
  }
}
