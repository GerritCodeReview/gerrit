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

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Project.SubmitType;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.RepositoryCache.FileKey;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Imports all projects found within the repository manager. */
public class GitProjectImporter {
  public interface Messages {
    void info(String msg);
    void warning(String msg);
  }

  private final LocalDiskRepositoryManager repositoryManager;
  private final SchemaFactory<ReviewDb> schema;
  private Messages messages;

  @Inject
  GitProjectImporter(final LocalDiskRepositoryManager repositoryManager,
      final SchemaFactory<ReviewDb> schema) {
    this.repositoryManager = repositoryManager;
    this.schema = schema;
  }

  public void run(final Messages msg) throws OrmException, IOException {
    messages = msg;
    messages.info("Scanning " + repositoryManager.getBasePath());
    final ReviewDb db = schema.open();
    try {
      final HashSet<String> have = new HashSet<String>();
      for (Project p : db.projects().all()) {
        have.add(p.getName());
      }
      importProjects(repositoryManager.getBasePath(), "", db, have);
    } finally {
      db.close();
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
      String name = f.getName();
      if (".".equals(name) || "..".equals(name)) {
        continue;
      }

      if (FileKey.isGitRepository(f)) {
        if (name.equals(".git")) {
          name = prefix.substring(0, prefix.length() - 1);

        } else if (name.endsWith(".git")) {
          name = prefix + name.substring(0, name.length() - 4);

        } else {
          name = prefix + name;
          if (!have.contains(name)) {
            messages.warning("Importing non-standard name '" + name + "'");
          }
        }

        if (have.contains(name)) {
          continue;
        }

        final Project.NameKey nameKey = new Project.NameKey(name);
        final Project p = new Project(nameKey, db.nextProjectId());

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
}
