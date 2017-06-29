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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.lib.Constants.R_TAGS;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.api.projects.DeleteTagsInput;
import com.google.gerrit.extensions.api.projects.ProjectApi;
import com.google.gerrit.extensions.api.projects.TagInfo;
import com.google.gerrit.extensions.api.projects.TagInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import java.util.HashMap;
import java.util.List;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class DeleteTagsIT extends AbstractDaemonTest {
  private static final ImmutableList<String> TAGS =
      ImmutableList.of("refs/tags/test-1", "refs/tags/test-2", "refs/tags/test-3", "test-4");

  @Before
  public void setUp() throws Exception {
    for (String name : TAGS) {
      project().tag(name).create(new TagInput());
    }
    assertTags(TAGS);
  }

  @Test
  public void deleteTags() throws Exception {
    HashMap<String, RevCommit> initialRevisions = initialRevisions(TAGS);
    DeleteTagsInput input = new DeleteTagsInput();
    input.tags = TAGS;
    project().deleteTags(input);
    assertTagsDeleted();
    assertRefUpdatedEvents(initialRevisions);
  }

  @Test
  public void deleteTagsForbidden() throws Exception {
    DeleteTagsInput input = new DeleteTagsInput();
    input.tags = TAGS;
    setApiUser(user);
    try {
      project().deleteTags(input);
      fail("Expected ResourceConflictException");
    } catch (ResourceConflictException e) {
      assertThat(e).hasMessageThat().isEqualTo(errorMessageForTags(TAGS));
    }
    setApiUser(admin);
    assertTags(TAGS);
  }

  @Test
  public void deleteTagsNotFound() throws Exception {
    DeleteTagsInput input = new DeleteTagsInput();
    List<String> tags = Lists.newArrayList(TAGS);
    tags.add("refs/tags/does-not-exist");
    input.tags = tags;
    try {
      project().deleteTags(input);
      fail("Expected ResourceConflictException");
    } catch (ResourceConflictException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(errorMessageForTags(ImmutableList.of("refs/tags/does-not-exist")));
    }
    assertTagsDeleted();
  }

  @Test
  public void deleteTagsNotFoundContinue() throws Exception {
    // If it fails on the first tag in the input, it should still
    // continue to process the remaining tags.
    DeleteTagsInput input = new DeleteTagsInput();
    List<String> tags = Lists.newArrayList("refs/tags/does-not-exist");
    tags.addAll(TAGS);
    input.tags = tags;
    try {
      project().deleteTags(input);
      fail("Expected ResourceConflictException");
    } catch (ResourceConflictException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(errorMessageForTags(ImmutableList.of("refs/tags/does-not-exist")));
    }
    assertTagsDeleted();
  }

  private String errorMessageForTags(List<String> tags) {
    StringBuilder message = new StringBuilder();
    for (String tag : tags) {
      message
          .append("Cannot delete ")
          .append(prefixRef(tag))
          .append(": it doesn't exist or you do not have permission ")
          .append("to delete it\n");
    }
    return message.toString();
  }

  private HashMap<String, RevCommit> initialRevisions(List<String> tags) throws Exception {
    HashMap<String, RevCommit> result = new HashMap<>();
    for (String tag : tags) {
      String ref = prefixRef(tag);
      result.put(ref, getRemoteHead(project, ref));
    }
    return result;
  }

  private void assertRefUpdatedEvents(HashMap<String, RevCommit> revisions) throws Exception {
    for (String tag : revisions.keySet()) {
      RevCommit revision = revisions.get(prefixRef(tag));
      eventRecorder.assertRefUpdatedEvents(
          project.get(), prefixRef(tag), null, revision, revision, null);
    }
  }

  private String prefixRef(String ref) {
    return ref.startsWith(R_TAGS) ? ref : R_TAGS + ref;
  }

  private ProjectApi project() throws Exception {
    return gApi.projects().name(project.get());
  }

  private void assertTags(List<String> expected) throws Exception {
    List<TagInfo> actualTags = project().tags().get();
    Iterable<String> actualNames = Iterables.transform(actualTags, b -> b.ref);
    assertThat(actualNames)
        .containsExactlyElementsIn(expected.stream().map(t -> prefixRef(t)).collect(toList()))
        .inOrder();
  }

  private void assertTagsDeleted() throws Exception {
    assertTags(ImmutableList.<String>of());
  }
}
