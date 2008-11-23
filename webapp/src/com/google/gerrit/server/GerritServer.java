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

import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.jdbc.Database;

import org.apache.commons.codec.binary.Base64;

import java.io.File;
import java.util.Collections;
import java.util.Properties;

/** Global server-side state for Gerrit. */
public class GerritServer {
  private static GerritServer impl;

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
      impl = new GerritServer();
    }
    return impl;
  }

  private final Database<ReviewDb> db;
  private final SystemConfig config;
  private final SignedToken xsrf;
  private final SignedToken account;

  private GerritServer() throws OrmException, XsrfException {
    db = createDatabase();
    config = readSystemConfig();
    if (config == null) {
      throw new OrmException("No " + SystemConfig.class.getName() + " found");
    }

    xsrf = new SignedToken(config.maxSessionAge, config.xsrfPrivateKey);
    account = new SignedToken(config.maxSessionAge, config.accountPrivateKey);
  }

  private Database<ReviewDb> createDatabase() throws OrmException {
    final String dbpath = new File("ReviewDb").getAbsolutePath();
    final boolean isnew = !new File(dbpath + ".data.db").exists();

    final Properties p = new Properties();
    p.setProperty("driver", "org.h2.Driver");
    p.setProperty("url", "jdbc:h2:file:" + dbpath);

    final Database<ReviewDb> db = new Database<ReviewDb>(p, ReviewDb.class);
    if (isnew) {
      final ReviewDb c = db.open();
      try {
        c.createSchema();

        final SystemConfig s = SystemConfig.create();
        s.xsrfPrivateKey = SignedToken.generateRandomKey();
        s.accountPrivateKey = SignedToken.generateRandomKey();
        c.systemConfig().insert(Collections.singleton(s));
      } finally {
        c.close();
      }
    }
    return db;
  }

  private SystemConfig readSystemConfig() throws OrmException {
    final ReviewDb c = db.open();
    try {
      return c.systemConfig().get(new SystemConfig.Key());
    } finally {
      c.close();
    }
  }

  /** Get the {@link ReviewDb} schema factory for the server. */
  public Database<ReviewDb> getDatabase() {
    return db;
  }

  /** Time (in seconds) that user sessions stay "signed in". */
  public int getSessionAge() {
    return config.maxSessionAge;
  }

  /** Get the signature support used to protect against XSRF attacks. */
  public SignedToken getXsrfToken() {
    return xsrf;
  }

  /** Get the signature support used to protect user identity cookies. */
  public SignedToken getAccountToken() {
    return account;
  }

  /** A binary string key to encrypt cookies related to account data. */
  public String getAccountCookieKey() {
    byte[] r = new byte[config.accountPrivateKey.length()];
    for (int k = r.length - 1; k >= 0; k--) {
      r[k] = (byte) config.accountPrivateKey.charAt(k);
    }
    r = Base64.decodeBase64(r);
    final StringBuilder b = new StringBuilder();
    for (int i = 0; i < r.length; i++) {
      b.append((char) r[i]);
    }
    return b.toString();
  }
}
