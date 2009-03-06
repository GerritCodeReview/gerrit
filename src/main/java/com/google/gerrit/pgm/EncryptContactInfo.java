// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ContactInformation;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.ContactInformationStoreException;
import com.google.gerrit.git.WorkQueue;
import com.google.gerrit.server.EncryptedContactStore;
import com.google.gerrit.server.GerritServer;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.jdbc.JdbcSchema;

import org.spearce.jgit.lib.ProgressMonitor;
import org.spearce.jgit.lib.TextProgressMonitor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

/** Export old contact columns to the encrypted contact store. */
public class EncryptContactInfo {
  public static void main(final String[] argv) throws OrmException,
      XsrfException, ContactInformationStoreException, SQLException {
    try {
      mainImpl(argv);
    } finally {
      WorkQueue.terminate();
    }
  }

  private static void mainImpl(final String[] argv) throws OrmException,
      XsrfException, ContactInformationStoreException, SQLException {
    final ProgressMonitor pm = new TextProgressMonitor();
    GerritServer.getInstance();
    final ReviewDb db = Common.getSchemaFactory().open();
    try {
      pm.start(1);
      pm.beginTask("Enumerate accounts", ProgressMonitor.UNKNOWN);
      final Connection sql = ((JdbcSchema) db).getConnection();
      final Statement stmt = sql.createStatement();
      final ResultSet rs =
          stmt.executeQuery("SELECT" + " account_id" + ",contact_address"
              + ",contact_country" + ",contact_phone_nbr" + ",contact_fax_nbr"
              + " FROM accounts WHERE contact_filed_on IS NOT NULL"
              + " ORDER BY account_id");
      final ArrayList<ToDo> todo = new ArrayList<ToDo>();
      while (rs.next()) {
        final ToDo d = new ToDo();
        d.id = new Account.Id(rs.getInt(1));
        d.info.setAddress(rs.getString(2));
        d.info.setCountry(rs.getString(3));
        d.info.setPhoneNumber(rs.getString(4));
        d.info.setFaxNumber(rs.getString(5));
        todo.add(d);
        pm.update(1);
      }
      rs.close();
      stmt.close();
      pm.endTask();

      pm.start(1);
      pm.beginTask("Store contact", todo.size());
      for (final ToDo d : todo) {
        final Account them = db.accounts().get(d.id);
        if (them.isContactFiled() && ContactInformation.hasData(d.info)) {
          EncryptedContactStore.store(them, d.info);
        }
        pm.update(1);
      }
      pm.endTask();
    } finally {
      db.close();
    }
  }

  static class ToDo {
    Account.Id id;
    final ContactInformation info = new ContactInformation();
  }
}
