// Copyright (C) 2011 The Android Open Source Project
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

import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.gerrit.common.data.MergeStrategySection;
import com.google.gerrit.common.data.RefConfigSection;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.git.ProjectConfig;

import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProjectStateTest extends TestCase {
  private ProjectConfig config;
  private ProjectCache cache;
  private ProjectState allProjectsState;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    config = createStrictMock(ProjectConfig.class);
    cache = createStrictMock(ProjectCache.class);
    allProjectsState = createStrictMock(ProjectState.class);
  }

  private void doReplay() {
    replay(config, cache, allProjectsState);
  }

  private void doVerify() {
    verify(config, cache, allProjectsState);
  }

  @Test
  public void testMergeStrategyWithOneOfAllProjects() throws Exception {
    final MergeStrategySection allProjectsSection =
        new MergeStrategySection(RefConfigSection.ALL);
    allProjectsSection
        .setSubmitType(MergeStrategySection.SubmitType.MERGE_IF_NECESSARY);

    execute(Collections.EMPTY_LIST, Collections
        .singletonList(allProjectsSection), allProjectsSection,
        "refs/heads/master");
  }

  @Test
  public void testMergeStrategyPrecedence() throws Exception {
    final MergeStrategySection oneProjectSectionToAllRefs =
        new MergeStrategySection(RefConfigSection.ALL);
    oneProjectSectionToAllRefs
        .setSubmitType(MergeStrategySection.SubmitType.MERGE_ALWAYS);

    final MergeStrategySection allProjectsSection =
        new MergeStrategySection(RefConfigSection.ALL);
    allProjectsSection
        .setSubmitType(MergeStrategySection.SubmitType.MERGE_IF_NECESSARY);

    execute(Collections.singletonList(oneProjectSectionToAllRefs), Collections
        .singletonList(allProjectsSection), oneProjectSectionToAllRefs,
        "refs/heads/master");
  }

  @Test
  public void testMergeStrategyMatch() throws Exception {
    final MergeStrategySection oneProjectSectionToMaster =
        new MergeStrategySection("refs/heads/master");
    oneProjectSectionToMaster
        .setSubmitType(MergeStrategySection.SubmitType.CHERRY_PICK);
    final MergeStrategySection oneProjectSectionToAllRefs =
        new MergeStrategySection(RefConfigSection.ALL);
    oneProjectSectionToAllRefs
        .setSubmitType(MergeStrategySection.SubmitType.MERGE_ALWAYS);
    final List<MergeStrategySection> oneProjectSections =
        new ArrayList<MergeStrategySection>();
    oneProjectSections.add(oneProjectSectionToMaster);
    oneProjectSections.add(oneProjectSectionToAllRefs);

    final MergeStrategySection allProjectsSection =
        new MergeStrategySection("refs/heads/master");
    allProjectsSection
        .setSubmitType(MergeStrategySection.SubmitType.MERGE_IF_NECESSARY);

    execute(oneProjectSections, Collections.singletonList(allProjectsSection),
        oneProjectSectionToMaster, "refs/heads/master");
  }

  @Test
  public void testMergeStrategyRegexMatch() throws Exception {
    final MergeStrategySection oneProjectSectionNotMatchingMaster =
        new MergeStrategySection("^refs/heads/m[0-1]+");
    oneProjectSectionNotMatchingMaster
        .setSubmitType(MergeStrategySection.SubmitType.CHERRY_PICK);
    final MergeStrategySection oneProjectSectionRegexMatchingMaster =
        new MergeStrategySection("^refs/heads/m[a-z]+");
    oneProjectSectionRegexMatchingMaster
        .setSubmitType(MergeStrategySection.SubmitType.FAST_FORWARD_ONLY);
    final MergeStrategySection oneProjectSectionToAllRefs =
        new MergeStrategySection(RefConfigSection.ALL);
    oneProjectSectionToAllRefs
        .setSubmitType(MergeStrategySection.SubmitType.MERGE_ALWAYS);
    final List<MergeStrategySection> oneProjectSections =
        new ArrayList<MergeStrategySection>();
    oneProjectSections.add(oneProjectSectionNotMatchingMaster);
    oneProjectSections.add(oneProjectSectionRegexMatchingMaster);
    oneProjectSections.add(oneProjectSectionToAllRefs);

    final MergeStrategySection allProjectsSection =
        new MergeStrategySection(RefConfigSection.ALL);
    allProjectsSection
        .setSubmitType(MergeStrategySection.SubmitType.MERGE_IF_NECESSARY);

    execute(oneProjectSections, Collections.singletonList(allProjectsSection),
        oneProjectSectionRegexMatchingMaster, "refs/heads/master");
  }

  private void execute(final List<MergeStrategySection> oneProjectSections,
      final List<MergeStrategySection> allProjectsSections,
      final MergeStrategySection mostSpecificWanted, final String destBranch)
      throws Exception {
    final AllProjectsName allProjects =
        new AllProjectsName(AllProjectsNameProvider.DEFAULT);
    final Project oneProject = new Project(new Project.NameKey("one-project"));

    // Expected calls in the ProjectState constructor.
    expect(config.getProject()).andReturn(oneProject);
    expect(config.getAccessSection(RefConfigSection.ALL)).andReturn(null);

    // Expected calls in the ProjectState#getMostSpecificMergeStrategy method

    // ProjectState#getAllMergeStrategySections
    // ProjectState#getLocalMergeStrategySections
    expect(config.getMergeStrategySections()).andReturn(oneProjectSections);
    // ProjectState#getInheritedMergeStrategySections
    expect(config.getProject()).andReturn(oneProject);
    expect(cache.get(allProjects)).andReturn(allProjectsState);
    // ProjectState#getLocalMergeStrategySections
    expect(allProjectsState.getLocalMergeStrategySections()).andReturn(
        allProjectsSections);

    doReplay();

    final ProjectState state =
        new ProjectState(cache, allProjects, null, null, null, null, config);

    assertEquals(mostSpecificWanted, state
        .getMostSpecificMergeStrategy("refs/heads/master"));

    doVerify();
  }
}
