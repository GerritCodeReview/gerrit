// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.TypeLiteral;

import org.eclipse.jgit.lib.Config;

import java.util.HashMap;

public class DashboardResource implements RestResource {
  public static final TypeLiteral<RestView<DashboardResource>> DASHBOARD_KIND =
      new TypeLiteral<RestView<DashboardResource>>() {};

  public interface Tokenizer {
    public String tokenize(String value);
  }

  public static class HashMapTokenizer extends HashMap<String, String>
      implements Tokenizer {
    @Override
    public String tokenize(String value) {
      return get(value);
    }
  }

  public static DashboardResource projectTyped(ProjectControl ctl,
      Project.DashboardType type) {
    return new DashboardResource(ctl, null, null, null, type);
  }

  public static DashboardResource projectTyped(ProjectControl ctl,
      Project.DashboardType type, Tokenizer tokenizer) {
    return new DashboardResource(ctl, null, null, null, type, tokenizer);
  }

  private final ProjectControl control;
  private final String refName;
  private final String pathName;
  private final Config config;
  private Project.DashboardType type;
  private Tokenizer tokenizer;

  DashboardResource(ProjectControl control,
      String refName,
      String pathName,
      Config config) {
    this(control, refName, pathName, config, null);
  }

  DashboardResource(ProjectControl control,
      String refName,
      String pathName,
      Config config,
      Project.DashboardType type) {
    this(control, refName, pathName, config, null, null);
  }

  DashboardResource(ProjectControl control,
      String refName,
      String pathName,
      Config config,
      Project.DashboardType type,
      Tokenizer tokenizer) {
    this.control = control;
    this.refName = refName;
    this.pathName = pathName;
    this.config = config;
    this.type = type;
    this.tokenizer = tokenizer;
  }

  public ProjectControl getControl() {
    return control;
  }

  public String getRefName() {
    return refName;
  }

  public String getPathName() {
    return pathName;
  }

  public Config getConfig() {
    return config;
  }

  public Project.DashboardType getType() {
    return type;
  }

  public Tokenizer getTokenizer() {
    return tokenizer;
  }
}
