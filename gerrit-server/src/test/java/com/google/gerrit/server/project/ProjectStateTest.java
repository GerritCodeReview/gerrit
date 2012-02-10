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

import com.google.gerrit.common.data.SubmitActionSection;
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
  public void testSubmitActionWithOneOfAllProjects() throws Exception {
    final SubmitActionSection allProjectsSection =
        new SubmitActionSection(RefConfigSection.ALL);
    allProjectsSection
        .setSubmitType(SubmitActionSection.SubmitType.MERGE_IF_NECESSARY);

    execute(Collections.EMPTY_LIST, Collections
        .singletonList(allProjectsSection), allProjectsSection,
        "refs/heads/master");
  }

  @Test
  public void testSubmitActionPrecedence() throws Exception {
    final SubmitActionSection oneProjectSectionToAllRefs =
        new SubmitActionSection(RefConfigSection.ALL);
    oneProjectSectionToAllRefs
        .setSubmitType(SubmitActionSection.SubmitType.MERGE_ALWAYS);

    final SubmitActionSection allProjectsSection =
        new SubmitActionSection(RefConfigSection.ALL);
    allProjectsSection
        .setSubmitType(SubmitActionSection.SubmitType.MERGE_IF_NECESSARY);

    execute(Collections.singletonList(oneProjectSectionToAllRefs), Collections
        .singletonList(allProjectsSection), oneProjectSectionToAllRefs,
        "refs/heads/master");
  }

  @Test
  public void testSubmitActionMatch() throws Exception {
    final SubmitActionSection oneProjectSectionToMaster =
        new SubmitActionSection("refs/heads/master");
    oneProjectSectionToMaster
        .setSubmitType(SubmitActionSection.SubmitType.CHERRY_PICK);
    final SubmitActionSection oneProjectSectionToAllRefs =
        new SubmitActionSection(RefConfigSection.ALL);
    oneProjectSectionToAllRefs
        .setSubmitType(SubmitActionSection.SubmitType.MERGE_ALWAYS);
    final List<SubmitActionSection> oneProjectSections =
        new ArrayList<SubmitActionSection>();
    oneProjectSections.add(oneProjectSectionToMaster);
    oneProjectSections.add(oneProjectSectionToAllRefs);

    final SubmitActionSection allProjectsSection =
        new SubmitActionSection("refs/heads/master");
    allProjectsSection
        .setSubmitType(SubmitActionSection.SubmitType.MERGE_IF_NECESSARY);

    execute(oneProjectSections, Collections.singletonList(allProjectsSection),
        oneProjectSectionToMaster, "refs/heads/master");
  }

  @Test
  public void testSubmitActionRegexMatch() throws Exception {
    final SubmitActionSection oneProjectSectionNotMatchingMaster =
        new SubmitActionSection("^refs/heads/m[0-1]+");
    oneProjectSectionNotMatchingMaster
        .setSubmitType(SubmitActionSection.SubmitType.CHERRY_PICK);
    final SubmitActionSection oneProjectSectionRegexMatchingMaster =
        new SubmitActionSection("^refs/heads/m[a-z]+");
    oneProjectSectionRegexMatchingMaster
        .setSubmitType(SubmitActionSection.SubmitType.FAST_FORWARD_ONLY);
    final SubmitActionSection oneProjectSectionToAllRefs =
        new SubmitActionSection(RefConfigSection.ALL);
    oneProjectSectionToAllRefs
        .setSubmitType(SubmitActionSection.SubmitType.MERGE_ALWAYS);
    final List<SubmitActionSection> oneProjectSections =
        new ArrayList<SubmitActionSection>();
    oneProjectSections.add(oneProjectSectionNotMatchingMaster);
    oneProjectSections.add(oneProjectSectionRegexMatchingMaster);
    oneProjectSections.add(oneProjectSectionToAllRefs);

    final SubmitActionSection allProjectsSection =
        new SubmitActionSection(RefConfigSection.ALL);
    allProjectsSection
        .setSubmitType(SubmitActionSection.SubmitType.MERGE_IF_NECESSARY);

    execute(oneProjectSections, Collections.singletonList(allProjectsSection),
        oneProjectSectionRegexMatchingMaster, "refs/heads/master");
  }

  @Test
  public void testMatchWithOnlyLocalMergeContent() throws Exception {
    final SubmitActionSection oneProjectSectionToMaster =
        new SubmitActionSection("refs/heads/master");
    oneProjectSectionToMaster
        .setUseContentMerge(SubmitActionSection.UseContentMerge.FALSE);

    final List<SubmitActionSection> oneProjectSections =
        new ArrayList<SubmitActionSection>();
    oneProjectSections.add(oneProjectSectionToMaster);

    final SubmitActionSection allProjectsSection =
        new SubmitActionSection(RefConfigSection.ALL);
    allProjectsSection
        .setUseContentMerge(SubmitActionSection.UseContentMerge.TRUE);
    allProjectsSection
        .setSubmitType(SubmitActionSection.SubmitType.MERGE_IF_NECESSARY);

    final SubmitActionSection expectedMostSpecificSection =
        new SubmitActionSection("refs/heads/master");
    expectedMostSpecificSection.setUseContentMerge(oneProjectSectionToMaster
        .isUseContentMerge());
    expectedMostSpecificSection.setSubmitType(allProjectsSection
        .getSubmitType());

    execute(oneProjectSections, Collections.singletonList(allProjectsSection),
        expectedMostSpecificSection, "refs/heads/master");
  }

  @Test
  public void testMatchWithOnlyLocalAction() throws Exception {
    final SubmitActionSection oneProjectSectionToMaster =
        new SubmitActionSection("refs/heads/master");
    oneProjectSectionToMaster
        .setSubmitType(SubmitActionSection.SubmitType.CHERRY_PICK);

    final List<SubmitActionSection> oneProjectSections =
        new ArrayList<SubmitActionSection>();
    oneProjectSections.add(oneProjectSectionToMaster);

    final SubmitActionSection allProjectsSectionAll =
        new SubmitActionSection(RefConfigSection.ALL);
    allProjectsSectionAll
        .setUseContentMerge(SubmitActionSection.UseContentMerge.TRUE);
    allProjectsSectionAll
        .setSubmitType(SubmitActionSection.SubmitType.MERGE_IF_NECESSARY);

    final SubmitActionSection allProjectsSectionMaster =
        new SubmitActionSection("refs/heads/master");
    allProjectsSectionMaster
        .setUseContentMerge(SubmitActionSection.UseContentMerge.TRUE);
    allProjectsSectionMaster
        .setSubmitType(SubmitActionSection.SubmitType.MERGE_IF_NECESSARY);

    final List<SubmitActionSection> allProjectsSections =
        new ArrayList<SubmitActionSection>();
    allProjectsSections.add(allProjectsSectionAll);
    allProjectsSections.add(allProjectsSectionMaster);

    final SubmitActionSection expectedMostSpecificSection =
        new SubmitActionSection("refs/heads/master");
    expectedMostSpecificSection.setUseContentMerge(allProjectsSectionMaster
        .isUseContentMerge());
    expectedMostSpecificSection.setSubmitType(oneProjectSectionToMaster
        .getSubmitType());

    execute(oneProjectSections, allProjectsSections,
        expectedMostSpecificSection, "refs/heads/master");
  }

  private void execute(final List<SubmitActionSection> oneProjectSections,
      final List<SubmitActionSection> allProjectsSections,
      final SubmitActionSection mostSpecificWanted, final String destBranch)
      throws Exception {
    final AllProjectsName allProjects =
        new AllProjectsName(AllProjectsNameProvider.DEFAULT);
    final Project oneProject = new Project(new Project.NameKey("one-project"));

    // Expected calls in the ProjectState constructor.
    expect(config.getProject()).andReturn(oneProject);
    expect(config.getAccessSection(RefConfigSection.ALL)).andReturn(null);

    // Expected calls in the ProjectState#getMostSpecificSubmitAction method

    // ProjectState#getAllSubmitActionSections
    // ProjectState#getLocalSubmitActionSections
    expect(config.getSubmitActionSections()).andReturn(oneProjectSections);
    // ProjectState#getInheritedSubmitActionSections
    expect(config.getProject()).andReturn(oneProject);
    expect(cache.get(allProjects)).andReturn(allProjectsState);
    // ProjectState#getLocalSubmitActionSections
    expect(allProjectsState.getLocalSubmitActionSections()).andReturn(
        allProjectsSections);

    doReplay();

    final ProjectState state =
        new ProjectState(cache, allProjects, null, null, null, null, config);

    assertEquals(mostSpecificWanted, state
        .getMostSpecificSubmitAction(destBranch));

    doVerify();
  }
}
