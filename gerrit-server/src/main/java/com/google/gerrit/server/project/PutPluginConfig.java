// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.common.base.Objects;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.ConfigEntries;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectLevelConfig;
import com.google.gerrit.server.project.PutPluginConfig.Input;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Singleton
public class PutPluginConfig implements RestModifyView<PluginConfigResource, Input> {
  public static class Input {
    public List<ConfigEntry> addEntries;
    public List<ConfigEntry> removeEntries;
  }

  public static class ConfigEntry {
    public String section;
    public String subSection;
    public String key;
    public String value;
  }

  private final MetaDataUpdate.User metaDataUpdateFactory;
  private final ProjectCache projectCache;
  private final Provider<CurrentUser> currentUser;
  private final ChangeHooks hooks;

  @Inject
  PutPluginConfig(MetaDataUpdate.User metaDataUpdateFactory,
      ProjectCache projectCache,
      ChangeHooks hooks,
      Provider<CurrentUser> currentUser) {
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.projectCache = projectCache;
    this.hooks = hooks;
    this.currentUser = currentUser;
  }

  @Override
  public ConfigEntries apply(PluginConfigResource rsrc, Input input)
      throws AuthException, BadRequestException, ResourceConflictException,
      Exception {
    Project.NameKey projectName = rsrc.getNameKey();
    ProjectLevelConfig cfg = rsrc.getConfig();
    if (!rsrc.getControl().isOwner() || cfg == null) {
      throw new ResourceNotFoundException(projectName.get());
    }
    String pluginName = rsrc.getPluginName();
    MetaDataUpdate md;
    try {
      md = metaDataUpdateFactory.create(projectName);
    } catch (RepositoryNotFoundException notFound) {
      throw new ResourceNotFoundException(projectName.get());
    } catch (IOException e) {
      throw new ResourceNotFoundException(projectName.get(), e);
    }
    try {
      cfg.load(md);
      writeEntriesToConfig(cfg.get(), input.addEntries);
      removeEntriesFromConfig(cfg.get(), input.removeEntries);
      md.setMessage("Modified " + pluginName + " configurations\n");
      try {
        ObjectId baseRev = cfg.getRevision();
        ObjectId commitRev = cfg.commit(md);
        // Only fire hook if project was actually changed.
        if (!Objects.equal(baseRev, commitRev)) {
          IdentifiedUser user = (IdentifiedUser) currentUser.get();
          hooks.doRefUpdatedHook(
            new Branch.NameKey(projectName, RefNames.REFS_CONFIG),
            baseRev, commitRev, user.getAccount());
        }
        projectCache.evict(projectName);
      } catch (IOException e) {
        if (e.getCause() instanceof ConfigInvalidException) {
          throw new ResourceConflictException("Cannot update " + projectName
              + ": " + e.getCause().getMessage());
        } else {
          throw new ResourceConflictException("Cannot update " + projectName);
        }
      }
    } catch (ConfigInvalidException err) {
      throw new ResourceConflictException("Cannot read " + pluginName
          + " configurations for project " + projectName, err);
    } catch (IOException err) {
      throw new ResourceConflictException("Cannot update " + pluginName
          + " configurations for project " + projectName, err);
    } finally {
      md.close();
    }
    return ConfigEntries.fromConfig(cfg.get(), rsrc.getFileName());
  }

  private void writeEntriesToConfig(Config cfg, List<ConfigEntry> entries) {
    if (entries == null) {
      return;
    }
    for (ConfigEntry entry : entries) {
      List<String> values = new ArrayList<>(Arrays.asList(cfg.getStringList(
          entry.section, entry.subSection, entry.key)));
      if (!values.contains(entry.value)) {
        values.add(entry.value);
      }
      cfg.setStringList(entry.section, entry.subSection, entry.key, values);
    }
  }

  private void removeEntriesFromConfig(Config cfg, List<ConfigEntry> entries) {
    if (entries == null) {
      return;
    }
    for (ConfigEntry entry : entries) {
      List<String> values = new ArrayList<>(Arrays.asList(cfg.getStringList(
          entry.section, entry.subSection, entry.key)));
      if (values.contains(entry.value)) {
        values.remove(entry.value);
      }
      cfg.unset(entry.section, entry.subSection, entry.key);
      cfg.setStringList(entry.section, entry.subSection, entry.key, values);
    }
  }
}
