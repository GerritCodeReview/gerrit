// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import static com.google.gerrit.server.git.ProjectConfig.ACCESS;
import static com.google.gerrit.server.git.ProjectConfig.KEY_GROUP_PERMISSIONS;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ProjectConfigSchemaUpdate extends VersionedMetaData {

  private final MetaDataUpdate update;
  private Config config;
  private boolean updated;

  public static ProjectConfigSchemaUpdate read(MetaDataUpdate update)
      throws IOException, ConfigInvalidException {
    ProjectConfigSchemaUpdate r = new ProjectConfigSchemaUpdate(update);
    r.load(update);
    return r;
  }

  private ProjectConfigSchemaUpdate(MetaDataUpdate update) {
    this.update = update;
  }

  @Override
  protected String getRefName() {
    return RefNames.REFS_CONFIG;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    config = readConfig(ProjectConfig.PROJECT_CONFIG);
  }

  public void renamePermission(final String oldName, final String newName) {
    for (String subsection : config.getSubsections(ACCESS)) {
      Set<String> names = config.getNames(ACCESS, subsection);
      if (names.contains(oldName)) {
        String[] values = config.getStringList(ACCESS, subsection, oldName);
        config.setStringList(ACCESS, subsection, newName, Arrays.asList(values));
        config.unset(ACCESS, subsection, oldName);
        updated = true;
      }

      List<String> exclusiveValues = new ArrayList<>();
      for (String varName : config.getStringList(ACCESS, subsection,
          KEY_GROUP_PERMISSIONS)) {
        List<String> exclusivePermissions =
            Arrays.asList(varName.split("[, \t]{1,}"));
        exclusivePermissions = new ArrayList<>(Lists
            .transform(exclusivePermissions, new Function<String, String>() {
              @Override
              public String apply(String exclusivePermission) {
                if (exclusivePermission.equals(oldName)) {
                  updated = true;
                  return newName;
                }
                return exclusivePermission;
              }
            }));
        Collections.sort(exclusivePermissions);
        StringBuilder exclusive = new StringBuilder();
        for (String perm : exclusivePermissions) {
          if (0 < exclusive.length()) {
            exclusive.append(' ');
          }
          exclusive.append(perm);
        }
        exclusiveValues.add(exclusive.toString());
      }
      config.setStringList(ACCESS, subsection, KEY_GROUP_PERMISSIONS,
          exclusiveValues);
    }
  }

  public void removeForceFromPermission(String name) {
    for (String subsection : config.getSubsections(ACCESS)) {
      Set<String> names = config.getNames(ACCESS, subsection);
      if (names.contains(name)) {
        List<String> values =
            Arrays.asList(config.getStringList(ACCESS, subsection, name));
        values = Lists.transform(values, new Function<String, String>() {
          @Override
          public String apply(String ruleString) {
            PermissionRule rule = PermissionRule.fromString(ruleString, false);
            if (rule.getForce()) {
              rule.setForce(false);
              updated = true;
            }
            return rule.asString(false);
          }
        });
        config.setStringList(ACCESS, subsection, name, values);
      }
    }
  }

  @Override
  protected boolean onSave(CommitBuilder commit)
      throws IOException, ConfigInvalidException {
    saveConfig(ProjectConfig.PROJECT_CONFIG, config);
    return true;
  }

  public void save(PersonIdent personIdent, String commitMessage)
      throws OrmException {
    if (!updated) {
      return;
    }

    update.getCommitBuilder().setAuthor(personIdent);
    update.getCommitBuilder().setCommitter(personIdent);
    update.setMessage(commitMessage);
    try {
      commit(update);
    } catch (IOException e) {
      throw new OrmException(e);
    }
  }
}
