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

package com.google.gerrit.client.rpc;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.data.AccountCache;
import com.google.gerrit.client.data.GerritConfig;
import com.google.gerrit.client.data.GroupCache;
import com.google.gerrit.client.data.ProjectCache;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gwt.core.client.GWT;
import com.google.gwtorm.client.SchemaFactory;

public class Common {
  public static final RpcConstants C;
  private static GerritConfig config;
  private static SchemaFactory<ReviewDb> schema;
  private static AccountCache accountCache;
  private static GroupCache groupCache;
  private static ProjectCache projectCache;
  private static CurrentAccountImpl caImpl;

  static {
    if (GWT.isClient()) {
      C = GWT.create(RpcConstants.class);
      caImpl = new CurrentAccountImpl() {
        public Account.Id getAccountId() {
          final Account a = Gerrit.getUserAccount();
          return a != null ? a.getId() : null;
        }
      };
    } else {
      C = null;
    }
  }

  /** Get the public configuration data used by this Gerrit instance. */
  public static GerritConfig getGerritConfig() {
    return config;
  }

  public static void setGerritConfig(final GerritConfig imp) {
    config = imp;
  }

  /**
   * Get the active AccountCache instance.
   * <p>
   * <b>Note: this is likely only available on the server side.</b>
   */
  public static AccountCache getAccountCache() {
    return accountCache;
  }

  public static void setAccountCache(final AccountCache imp) {
    accountCache = imp;
  }

  /**
   * Get the active GroupCache instance.
   * <p>
   * <b>Note: this is likely only available on the server side.</b>
   */
  public static GroupCache getGroupCache() {
    return groupCache;
  }

  public static void setGroupCache(final GroupCache imp) {
    groupCache = imp;
  }

  /**
   * Get the active ProjectCache instance.
   * <p>
   * <b>Note: this is likely only available on the server side.</b>
   */
  public static ProjectCache getProjectCache() {
    return projectCache;
  }

  public static void setProjectCache(final ProjectCache imp) {
    projectCache = imp;
  }

  /**
   * Get the schema factory for this instance.
   * <p>
   * <b>Note: this is likely only available on the server side.</b>
   */
  public static SchemaFactory<ReviewDb> getSchemaFactory() {
    return schema;
  }

  public static void setSchemaFactory(final SchemaFactory<ReviewDb> imp) {
    schema = imp;
  }

  /** Get the unique id for this account; null if there is no account. */
  public static Account.Id getAccountId() {
    return caImpl.getAccountId();
  }

  public static void setCurrentAccountImpl(final CurrentAccountImpl i) {
    caImpl = i;
  }

  public interface CurrentAccountImpl {
    /** Get the unique id for this account; null if there is no account. */
    public Account.Id getAccountId();
  }
}
