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

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.rest.util.RestApiCallHelper;
import com.google.gerrit.acceptance.rest.util.RestCall;
import com.google.gerrit.acceptance.rest.util.RestCall.Method;
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
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.httpd.restapi.RestApiServlet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.servlet.ServletModule;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import org.junit.Test;

/**
 * Tests for checking plugin-provided REST API bindings directly under {@code /}.
 *
 * <p>These tests only verify that the plugin-provided REST endpoints are correctly bound, they do
 * not test the functionality of the plugin REST endpoints.
 */
public class PluginProvidedRootRestApiBindingsIT extends AbstractDaemonTest {

  /** Resource to bind a child collection. */
  public static final TypeLiteral<RestView<TestPluginResource>> TEST_KIND =
      new TypeLiteral<RestView<TestPluginResource>>() {};

  private static final String PLUGIN_NAME = "my-plugin";

  private static final ImmutableSet<RestCall> TEST_CALLS =
      ImmutableSet.of(
          RestCall.get("/plugins/" + PLUGIN_NAME + "/test-collection/"),
          RestCall.get("/plugins/" + PLUGIN_NAME + "/test-collection/1/detail"),
          RestCall.post("/plugins/" + PLUGIN_NAME + "/test-collection/"),
          RestCall.post("/plugins/" + PLUGIN_NAME + "/test-collection/1/update"),
          RestCall.builder(Method.GET, "/plugins/" + PLUGIN_NAME + "/not-found")
              .expectedResponseCode(SC_NOT_FOUND)
              .build());

  /** Module for all HTTP bindings. */
  static class MyPluginHttpModule extends ServletModule {
    @Override
    public void configureServlets() {
      bind(TestRootCollection.class);

      install(
          new RestApiModule() {
            @Override
            public void configure() {
              DynamicMap.mapOf(binder(), TEST_KIND);

              postOnCollection(TEST_KIND).to(TestPostOnCollection.class);
              post(TEST_KIND, "update").to(TestPost.class);
              get(TEST_KIND, "detail").to(TestGet.class);
            }
          });

      serveRegex("/(?:a/)?test-collection/(.*)$").with(TestRestApiServlet.class);
    }
  }

  @Singleton
  static class TestRestApiServlet extends RestApiServlet {
    private static final long serialVersionUID = 1L;

    @Inject
    TestRestApiServlet(RestApiServlet.Globals globals, Provider<TestRootCollection> collection) {
      super(globals, collection);
    }

    @Override
    public void service(ServletRequest servletRequest, ServletResponse servletResponse)
        throws ServletException, IOException {
      // This is...unfortunate. HttpPluginServlet (and/or ContextMapper) doesn't properly set the
      // servlet path on the wrapped request. Based on what RestApiServlet produces for non-plugin
      // requests, it should be:
      //   contextPath = "/plugins/checks"
      //   servletPath = "/checkers/"
      //   pathInfo = checkerUuid
      // Instead it does:
      //   contextPath = "/plugins/checks"
      //   servletPath = ""
      //   pathInfo = "/checkers/" + checkerUuid
      // This results in RestApiServlet splitting the pathInfo into ["", "checkers", checkerUuid],
      // and it passes the "" to CheckersCollection#parse, which understandably, but unfortunately,
      // fails.
      //
      // This frankly seems like a bug that should be fixed, but it would quite likely break
      // existing plugins in confusing ways. So, we work around it by introducing our own request
      // wrapper with the correct paths.
      HttpServletRequest req = (HttpServletRequest) servletRequest;
      String pathInfo = req.getPathInfo();
      String correctServletPath = "/test-collection/";
      String fixedPathInfo = pathInfo.substring(correctServletPath.length());
      HttpServletRequestWrapper wrapped =
          new HttpServletRequestWrapper(req) {
            @Override
            public String getServletPath() {
              return correctServletPath;
            }

            @Override
            public String getPathInfo() {
              return fixedPathInfo;
            }
          };

      super.service(wrapped, (HttpServletResponse) servletResponse);
    }
  }

  static class TestPluginResource implements RestResource {}

  @Singleton
  static class TestRootCollection implements ChildCollection<TopLevelResource, TestPluginResource> {
    private final DynamicMap<RestView<TestPluginResource>> views;

    @Inject
    TestRootCollection(DynamicMap<RestView<TestPluginResource>> views) {
      this.views = views;
    }

    @Override
    public RestView<TopLevelResource> list() throws RestApiException {
      return (RestReadView<TopLevelResource>) resource -> ImmutableList.of("one", "two");
    }

    @Override
    public TestPluginResource parse(TopLevelResource parent, IdString id) throws Exception {
      return new TestPluginResource();
    }

    @Override
    public DynamicMap<RestView<TestPluginResource>> views() {
      return views;
    }
  }

  @Singleton
  static class TestPostOnCollection
      implements RestCollectionModifyView<TopLevelResource, TestPluginResource, String> {
    @Override
    public Object apply(TopLevelResource parentResource, String input) throws Exception {
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
    try (AutoCloseable ignored = installPlugin(PLUGIN_NAME, null, MyPluginHttpModule.class, null)) {
      RestApiCallHelper.execute(adminRestSession, TEST_CALLS.asList());
    }
  }
}
