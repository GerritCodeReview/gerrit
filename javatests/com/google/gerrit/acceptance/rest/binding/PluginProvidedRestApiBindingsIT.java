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
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.collect.ImmutableList;
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
import com.google.inject.servlet.ServletModule;
import java.io.IOException;
import java.util.Optional;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for checking plugin-provided REST API bindings.
 *
 * <p>These tests only verify that the plugin-provided REST endpoints are correctly bound, they do
 * not test the functionality of the plugin REST endpoints.
 */
public class PluginProvidedRestApiBindingsIT extends AbstractDaemonTest {

  /**
   * Plugin REST endpoints bound by {@link MyPluginHttpModule} with Guice serlvet definitions.
   *
   * <p>Each URL contains a placeholder for the plugin identifier.
   *
   * <p>Currently does not include any resource or documentation URLs, since those would require
   * installing a plugin from a jar, which is trickier than just defining a module in this file.
   */
  private static final ImmutableList<RestCall> SERVER_TOP_LEVEL_PLUGIN_ENDPOINTS =
      ImmutableList.of(RestCall.get("/plugins/%s/hello"));

  /** Resource to bind a child collection. */
  public static final TypeLiteral<RestView<TestPluginResource>> TEST_KIND =
      new TypeLiteral<RestView<TestPluginResource>>() {};

  private static final String PLUGIN_NAME = "my-plugin";

  /** Module for all HTTP bindings. */
  static class MyPluginHttpModule extends ServletModule {
    @Override
    public void configureServlets() {
      serve("/hello").with(HelloServlet.class);
    }
  }

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

  /** Servlet to bind a root collection for the plugin. */
  @Singleton
  static class HelloServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
      res.setStatus(SC_OK);
      res.getWriter().println("Hello world");
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

  private Optional<AutoCloseable> pluginContext = Optional.empty();

  @Before
  public void setUp() throws Exception {
    pluginContext =
        Optional.of(
            installPlugin(PLUGIN_NAME, MyPluginSysModule.class, MyPluginHttpModule.class, null));
  }

  @After
  public void tearDown() throws Exception {
    if (pluginContext.isPresent()) {
      pluginContext.get().close();
      pluginContext = Optional.empty();
    }
  }

  @Test
  public void serverPluginTopLevelEndpoints() throws Exception {
    RestApiCallHelper.execute(adminRestSession, SERVER_TOP_LEVEL_PLUGIN_ENDPOINTS, PLUGIN_NAME);
  }

  @Test
  public void childCollectionToCoreCollection() throws Exception {
    RestApiCallHelper.execute(adminRestSession, SERVER_TOP_LEVEL_PLUGIN_ENDPOINTS, PLUGIN_NAME);
  }

  @Test
  public void listOnChildCollection() throws Exception {
    PatchSet.Id patchSetId = createChange().getPatchSetId();
    RestApiCallHelper.execute(
        adminRestSession,
        RestCall.get("/changes/%s/revisions/%s/test-collection/"),
        String.valueOf(patchSetId.changeId.id),
        String.valueOf(patchSetId.patchSetId));
  }

  @Test
  public void getOnChildCollectionWithResource() throws Exception {
    PatchSet.Id patchSetId = createChange().getPatchSetId();
    RestApiCallHelper.execute(
        adminRestSession,
        RestCall.get("/changes/%s/revisions/%s/test-collection/1/detail"),
        String.valueOf(patchSetId.changeId.id),
        String.valueOf(patchSetId.patchSetId));
  }

  @Test
  public void postOnChildCollection() throws Exception {
    PatchSet.Id patchSetId = createChange().getPatchSetId();
    RestApiCallHelper.execute(
        adminRestSession,
        RestCall.post("/changes/%s/revisions/%s/test-collection/"),
        String.valueOf(patchSetId.changeId.id),
        String.valueOf(patchSetId.patchSetId));
  }

  @Test
  public void postOnChildCollectionWithResource() throws Exception {
    PatchSet.Id patchSetId = createChange().getPatchSetId();
    RestApiCallHelper.execute(
        adminRestSession,
        RestCall.post("/changes/%s/revisions/%s/test-collection/1/update"),
        String.valueOf(patchSetId.changeId.id),
        String.valueOf(patchSetId.patchSetId));
  }
}
