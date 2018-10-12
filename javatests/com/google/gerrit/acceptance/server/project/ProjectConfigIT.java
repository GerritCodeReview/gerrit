// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.project.ProjectState.INHERITED_FROM_GLOBAL;
import static com.google.gerrit.server.project.ProjectState.INHERITED_FROM_PARENT;
import static com.google.gerrit.server.project.ProjectState.OVERRIDDEN_BY_GLOBAL;
import static com.google.gerrit.server.project.ProjectState.OVERRIDDEN_BY_PARENT;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.api.projects.ConfigInfo;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Project;
import org.junit.Test;

@NoHttpd
public class ProjectConfigIT extends AbstractDaemonTest {
  @Test
  public void maxObjectSizeIsNotSetByDefault() throws Exception {
    ConfigInfo info = getConfig();
    assertThat(info.maxObjectSizeLimit.value).isNull();
    assertThat(info.maxObjectSizeLimit.configuredValue).isNull();
    assertThat(info.maxObjectSizeLimit.summary).isNull();
  }

  @Test
  public void maxObjectSizeCanBeSetAndCleared() throws Exception {
    // Set a value
    ConfigInfo info = setMaxObjectSize("100k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("102400");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("100k");
    assertThat(info.maxObjectSizeLimit.summary).isNull();

    // Clear the value
    info = setMaxObjectSize("0");
    assertThat(info.maxObjectSizeLimit.value).isNull();
    assertThat(info.maxObjectSizeLimit.configuredValue).isNull();
    assertThat(info.maxObjectSizeLimit.summary).isNull();
  }

  @Test
  @GerritConfig(name = "receive.inheritProjectMaxObjectSizeLimit", value = "true")
  public void maxObjectSizeIsInheritedFromParentProject() throws Exception {
    Project.NameKey child = createProject(name("child"), project);

    ConfigInfo info = setMaxObjectSize("100k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("102400");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("100k");
    assertThat(info.maxObjectSizeLimit.summary).isNull();

    info = getConfig(child);
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("102400");
    assertThat(info.maxObjectSizeLimit.configuredValue).isNull();
    assertThat(info.maxObjectSizeLimit.summary)
        .isEqualTo(String.format(INHERITED_FROM_PARENT, project));
  }

  @Test
  public void maxObjectSizeIsNotInheritedFromParentProject() throws Exception {
    Project.NameKey child = createProject(name("child"), project);

    ConfigInfo info = setMaxObjectSize("100k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("102400");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("100k");
    assertThat(info.maxObjectSizeLimit.summary).isNull();

    info = getConfig(child);
    assertThat(info.maxObjectSizeLimit.value).isNull();
    assertThat(info.maxObjectSizeLimit.configuredValue).isNull();
    assertThat(info.maxObjectSizeLimit.summary).isNull();
  }

  @Test
  public void maxObjectSizeOverridesParentProjectWhenNotSetOnParent() throws Exception {
    Project.NameKey child = createProject(name("child"), project);

    ConfigInfo info = setMaxObjectSize("0");
    assertThat(info.maxObjectSizeLimit.value).isNull();
    assertThat(info.maxObjectSizeLimit.configuredValue).isNull();
    assertThat(info.maxObjectSizeLimit.summary).isNull();

    info = setMaxObjectSize(child, "100k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("102400");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("100k");
    assertThat(info.maxObjectSizeLimit.summary).isNull();
  }

  @Test
  public void maxObjectSizeOverridesParentProjectWhenLower() throws Exception {
    Project.NameKey child = createProject(name("child"), project);

    ConfigInfo info = setMaxObjectSize("200k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("204800");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("200k");
    assertThat(info.maxObjectSizeLimit.summary).isNull();

    info = setMaxObjectSize(child, "100k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("102400");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("100k");
    assertThat(info.maxObjectSizeLimit.summary).isNull();
  }

  @Test
  @GerritConfig(name = "receive.inheritProjectMaxObjectSizeLimit", value = "true")
  public void maxObjectSizeDoesNotOverrideParentProjectWhenHigher() throws Exception {
    Project.NameKey child = createProject(name("child"), project);

    ConfigInfo info = setMaxObjectSize("100k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("102400");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("100k");
    assertThat(info.maxObjectSizeLimit.summary).isNull();

    info = setMaxObjectSize(child, "200k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("102400");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("200k");
    assertThat(info.maxObjectSizeLimit.summary)
        .isEqualTo(String.format(OVERRIDDEN_BY_PARENT, project));
  }

  @Test
  @GerritConfig(name = "receive.maxObjectSizeLimit", value = "200k")
  public void maxObjectSizeIsInheritedFromGlobalConfig() throws Exception {
    Project.NameKey child = createProject(name("child"), project);

    ConfigInfo info = getConfig();
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("204800");
    assertThat(info.maxObjectSizeLimit.configuredValue).isNull();
    assertThat(info.maxObjectSizeLimit.summary).isEqualTo(INHERITED_FROM_GLOBAL);

    info = getConfig(child);
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("204800");
    assertThat(info.maxObjectSizeLimit.configuredValue).isNull();
    assertThat(info.maxObjectSizeLimit.summary).isEqualTo(INHERITED_FROM_GLOBAL);
  }

  @Test
  @GerritConfig(name = "receive.maxObjectSizeLimit", value = "200k")
  public void maxObjectSizeOverridesGlobalConfigWhenLower() throws Exception {
    ConfigInfo info = setMaxObjectSize("100k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("102400");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("100k");
    assertThat(info.maxObjectSizeLimit.summary).isNull();
  }

  @Test
  @GerritConfig(name = "receive.maxObjectSizeLimit", value = "300k")
  public void inheritedMaxObjectSizeOverridesGlobalConfigWhenLower() throws Exception {
    Project.NameKey child = createProject(name("child"), project);

    ConfigInfo info = setMaxObjectSize("200k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("204800");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("200k");
    assertThat(info.maxObjectSizeLimit.summary).isNull();

    info = setMaxObjectSize(child, "100k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("102400");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("100k");
    assertThat(info.maxObjectSizeLimit.summary).isNull();
  }

  @Test
  @GerritConfig(name = "receive.maxObjectSizeLimit", value = "200k")
  @GerritConfig(name = "receive.inheritProjectMaxObjectSizeLimit", value = "true")
  public void maxObjectSizeDoesNotOverrideGlobalConfigWhenHigher() throws Exception {
    Project.NameKey child = createProject(name("child"), project);

    ConfigInfo info = setMaxObjectSize("300k");
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("204800");
    assertThat(info.maxObjectSizeLimit.configuredValue).isEqualTo("300k");
    assertThat(info.maxObjectSizeLimit.summary).isEqualTo(OVERRIDDEN_BY_GLOBAL);

    info = getConfig(child);
    assertThat(info.maxObjectSizeLimit.value).isEqualTo("204800");
    assertThat(info.maxObjectSizeLimit.configuredValue).isNull();
    assertThat(info.maxObjectSizeLimit.summary).isEqualTo(OVERRIDDEN_BY_GLOBAL);
  }

  @Test
  public void invalidMaxObjectSizeIsRejected() throws Exception {
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("100 foo");
    setMaxObjectSize("100 foo");
  }

  private ConfigInfo setMaxObjectSize(String value) throws Exception {
    return setMaxObjectSize(project, value);
  }

  private ConfigInfo setMaxObjectSize(Project.NameKey name, String value) throws Exception {
    ConfigInput input = new ConfigInput();
    input.maxObjectSizeLimit = value;
    return setConfig(name, input);
  }

  private ConfigInfo setConfig(Project.NameKey name, ConfigInput input) throws Exception {
    return gApi.projects().name(name.get()).config(input);
  }

  private ConfigInfo getConfig(Project.NameKey name) throws Exception {
    return gApi.projects().name(name.get()).config();
  }

  private ConfigInfo getConfig() throws Exception {
    return getConfig(project);
  }
}
