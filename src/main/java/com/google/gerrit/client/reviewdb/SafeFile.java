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

package com.google.gerrit.client.reviewdb;

import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.server.GerritServer;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.IntKey;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;

import java.util.List;
import java.util.Set;

/**
 * The representation of a file that is safe to link directly (e.g. an image). When asked to
 * generate a link to a file, CatServlet will look up the safe_files database to determine if
 * the file is safe. If it is, it will serve the raw content of this file, otherwise it will
 * return a zipped version of that file.
 */
public class SafeFile {
  /** Key local to Gerrit to identify a safe file. */
  public static class Id extends IntKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column
    protected int id;

    protected Id() {
    }

    public Id(final int id) {
      this.id = id;
    }

    @Override
    public int get() {
      return id;
    }

    @Override
    protected void set(int newValue) {
      id = newValue;
    }

  }

  @Column
  protected Id id;

  @Column
  protected String fileExtension;

  public String getFileExtension() {
    return fileExtension;
  }

}
