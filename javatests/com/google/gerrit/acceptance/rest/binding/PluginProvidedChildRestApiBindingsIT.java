// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.binding;

import static com.google.gerrit.server.change.RevisionResource.REVISION_KIND;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.rest.util.RestApiCallHelper;
import com.google.gerrit.acceptance.rest.util.RestCall;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.change.RevisionResource;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import org.junit.Test;

/**
 * Tests for checking plugin-provided REST API bindings nested under a core collection.
 *
 * <p>These tests only verify that the plugin-provided REST endpoints are correctly bound, they do
 * not test the functionality of the plugin REST endpoints.
 */
public class PluginProvidedChildRestApiBindingsIT extends AbstractDaemonTest {

  /** Resource to bind a child collection. */
  public static final TypeLiteral<RestView<TestPluginResource>> TEST_KIND =
      new TypeLiteral<RestView<TestPluginResource>>() {};

  private static final String PLUGIN_NAME = "my-plugin";

  private static final ImmutableSet<RestCall> TEST_CALLS =
      ImmutableSet.of(
          // Calls that have the plugin name as part of the collection name
          RestCall.get("/changes/%s/revisions/%s/" + PLUGIN_NAME + "~test-collection/"),
          RestCall.get("/changes/%s/revisions/%s/" + PLUGIN_NAME + "~test-collection/1/detail"),
          RestCall.post("/changes/%s/revisions/%s/" + PLUGIN_NAME + "~test-collection/"),
          RestCall.post("/changes/%s/revisions/%s/" + PLUGIN_NAME + "~test-collection/1/update"),
          // Same tests but without the plugin name as part of the collection name. This works as
          // long as there is no core collection with the same name (which takes precedence) and no
          // other plugin binds a collection with the same name. We highly encourage plugin authors
          // to use the fully qualified collection name instead.
          RestCall.get("/changes/%s/revisions/%s/test-collection/"),
          RestCall.get("/changes/%s/revisions/%s/test-collection/1/detail"),
          RestCall.post("/changes/%s/revisions/%s/test-collection/"),
          RestCall.post("/changes/%s/revisions/%s/test-collection/1/update"));

  /**
   * Module for all sys bindings.
   *
   * <p>TODO: This should actually just move into MyPluginHttpModule. However, that doesn't work
   * currently. This TODO is for fixing this bug.
   */
  static class MyPluginSysModule extends AbstractModule {
    @Override
    public void configure() {
      install(
          new RestApiModule() {
            @Override
            public void configure() {
              DynamicMap.mapOf(binder(), TEST_KIND);
              child(REVISION_KIND, "test-collection").to(TestChildCollection.class);

              postOnCollection(TEST_KIND).to(TestPostOnCollection.class);
              post(TEST_KIND, "update").to(TestPost.class);
              get(TEST_KIND, "detail").to(TestGet.class);
            }
          });
    }
  }

  static class TestPluginResource implements RestResource {}

  @Singleton
  static class TestChildCollection
      implements ChildCollection<RevisionResource, TestPluginResource> {
    private final DynamicMap<RestView<TestPluginResource>> views;

    @Inject
    TestChildCollection(DynamicMap<RestView<TestPluginResource>> views) {
      this.views = views;
    }

    @Override
    public RestView<RevisionResource> list() throws RestApiException {
      return (RestReadView<RevisionResource>) resource -> ImmutableList.of("one", "two");
    }

    @Override
    public TestPluginResource parse(RevisionResource parent, IdString id) throws Exception {
      return new TestPluginResource();
    }

    @Override
    public DynamicMap<RestView<TestPluginResource>> views() {
      return views;
    }
  }

  @Singleton
  static class TestPostOnCollection
      implements RestCollectionModifyView<RevisionResource, TestPluginResource, String> {
    @Override
    public Object apply(RevisionResource parentResource, String input) throws Exception {
      return "test";
    }
  }

  @Singleton
  static class TestPost implements RestModifyView<TestPluginResource, String> {
    @Override
    public String apply(TestPluginResource resource, String input) throws Exception {
      return "test";
    }
  }

  @Singleton
  static class TestGet implements RestReadView<TestPluginResource> {
    @Override
    public String apply(TestPluginResource resource) throws Exception {
      return "test";
    }
  }

  @Test
  public void testEndpoints() throws Exception {
    PatchSet.Id patchSetId = createChange().getPatchSetId();
    try (AutoCloseable ignored = installPlugin(PLUGIN_NAME, MyPluginSysModule.class, null, null)) {
      RestApiCallHelper.execute(
          adminRestSession,
          TEST_CALLS.asList(),
          String.valueOf(patchSetId.changeId().get()),
          String.valueOf(patchSetId.get()));
    }
  }
}
