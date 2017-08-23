// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.httpd;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.eq;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.ReloadableRegistrationHandle;
import com.google.gerrit.server.plugins.Plugin;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.gerrit.util.http.testutil.FakeHttpServletResponse;
import com.google.inject.Key;
import com.google.inject.util.Providers;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.easymock.Capture;
import org.easymock.EasyMockSupport;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;

public class AllRequestFilterFilterProxyTest {
  /**
   * Set of filters for FilterProxy
   *
   * <p>This set is used to as set of filters when fetching an {@link AllRequestFilter.FilterProxy}
   * instance through {@link #getFilterProxy()}.
   */
  private DynamicSet<AllRequestFilter> filters;

  @Before
  public void setUp() throws Exception {
    // Force starting each test with an initially empty set of filters.
    // Filters get added by the tests themselves.
    filters = new DynamicSet<>();
  }

  // The wrapping of {@link #getFilterProxy()} and
  // {@link #addFilter(AllRequestFilter)} into separate methods may seem
  // overengineered at this point. However, if in the future we decide to not
  // test the inner class directly, but rather test from the outside using
  // Guice Injectors, it is now sufficient to change only those two methods,
  // and we need not mess with the individual tests.

  /**
   * Obtain a FilterProxy with a known DynamicSet of filters
   *
   * <p>The returned {@link AllRequestFilter.FilterProxy} can have new filters added dynamically by
   * calling {@link #addFilter(AllRequestFilter)}.
   */
  private AllRequestFilter.FilterProxy getFilterProxy() {
    return new AllRequestFilter.FilterProxy(filters);
  }

  /**
   * Add a filter to created FilterProxy instances
   *
   * <p>This method adds the given filter to all {@link AllRequestFilter.FilterProxy} instances
   * created by {@link #getFilterProxy()}.
   */
  private ReloadableRegistrationHandle<AllRequestFilter> addFilter(AllRequestFilter filter) {
    Key<AllRequestFilter> key = Key.get(AllRequestFilter.class);
    return filters.add(key, Providers.of(filter));
  }

  @Test
  public void noFilters() throws Exception {
    EasyMockSupport ems = new EasyMockSupport();

    FilterConfig config = ems.createMock(FilterConfig.class);
    HttpServletRequest req = new FakeHttpServletRequest();
    HttpServletResponse res = new FakeHttpServletResponse();

    FilterChain chain = ems.createMock(FilterChain.class);
    chain.doFilter(req, res);

    ems.replayAll();

    AllRequestFilter.FilterProxy filterProxy = getFilterProxy();

    filterProxy.init(config);
    filterProxy.doFilter(req, res, chain);
    filterProxy.destroy();

    ems.verifyAll();
  }

  @Test
  public void singleFilterNoBubbling() throws Exception {
    EasyMockSupport ems = new EasyMockSupport();

    FilterConfig config = ems.createMock("config", FilterConfig.class);
    HttpServletRequest req = new FakeHttpServletRequest();
    HttpServletResponse res = new FakeHttpServletResponse();

    FilterChain chain = ems.createMock("chain", FilterChain.class);

    AllRequestFilter filter = ems.createStrictMock("filter", AllRequestFilter.class);
    filter.init(config);
    filter.doFilter(eq(req), eq(res), anyObject(FilterChain.class));
    filter.destroy();

    ems.replayAll();

    AllRequestFilter.FilterProxy filterProxy = getFilterProxy();
    addFilter(filter);

    filterProxy.init(config);
    filterProxy.doFilter(req, res, chain);
    filterProxy.destroy();

    ems.verifyAll();
  }

  @Test
  public void singleFilterBubbling() throws Exception {
    EasyMockSupport ems = new EasyMockSupport();

    FilterConfig config = ems.createMock(FilterConfig.class);
    HttpServletRequest req = new FakeHttpServletRequest();
    HttpServletResponse res = new FakeHttpServletResponse();

    IMocksControl mockControl = ems.createStrictControl();
    FilterChain chain = mockControl.createMock(FilterChain.class);

    Capture<FilterChain> capturedChain = new Capture<>();

    AllRequestFilter filter = mockControl.createMock(AllRequestFilter.class);
    filter.init(config);
    filter.doFilter(eq(req), eq(res), capture(capturedChain));
    chain.doFilter(req, res);
    filter.destroy();

    ems.replayAll();

    AllRequestFilter.FilterProxy filterProxy = getFilterProxy();
    addFilter(filter);

    filterProxy.init(config);
    filterProxy.doFilter(req, res, chain);
    capturedChain.getValue().doFilter(req, res);
    filterProxy.destroy();

    ems.verifyAll();
  }

  @Test
  public void twoFiltersNoBubbling() throws Exception {
    EasyMockSupport ems = new EasyMockSupport();

    FilterConfig config = ems.createMock(FilterConfig.class);
    HttpServletRequest req = new FakeHttpServletRequest();
    HttpServletResponse res = new FakeHttpServletResponse();

    IMocksControl mockControl = ems.createStrictControl();
    FilterChain chain = mockControl.createMock(FilterChain.class);

    AllRequestFilter filterA = mockControl.createMock(AllRequestFilter.class);

    AllRequestFilter filterB = mockControl.createMock(AllRequestFilter.class);
    filterA.init(config);
    filterB.init(config);
    filterA.doFilter(eq(req), eq(res), anyObject(FilterChain.class));
    filterA.destroy();
    filterB.destroy();

    ems.replayAll();

    AllRequestFilter.FilterProxy filterProxy = getFilterProxy();
    addFilter(filterA);
    addFilter(filterB);

    filterProxy.init(config);
    filterProxy.doFilter(req, res, chain);
    filterProxy.destroy();

    ems.verifyAll();
  }

  @Test
  public void twoFiltersBubbling() throws Exception {
    EasyMockSupport ems = new EasyMockSupport();

    FilterConfig config = ems.createMock(FilterConfig.class);
    HttpServletRequest req = new FakeHttpServletRequest();
    HttpServletResponse res = new FakeHttpServletResponse();

    IMocksControl mockControl = ems.createStrictControl();
    FilterChain chain = mockControl.createMock(FilterChain.class);

    Capture<FilterChain> capturedChainA = new Capture<>();
    Capture<FilterChain> capturedChainB = new Capture<>();

    AllRequestFilter filterA = mockControl.createMock(AllRequestFilter.class);
    AllRequestFilter filterB = mockControl.createMock(AllRequestFilter.class);

    filterA.init(config);
    filterB.init(config);
    filterA.doFilter(eq(req), eq(res), capture(capturedChainA));
    filterB.doFilter(eq(req), eq(res), capture(capturedChainB));
    chain.doFilter(req, res);
    filterA.destroy();
    filterB.destroy();

    ems.replayAll();

    AllRequestFilter.FilterProxy filterProxy = getFilterProxy();
    addFilter(filterA);
    addFilter(filterB);

    filterProxy.init(config);
    filterProxy.doFilter(req, res, chain);
    capturedChainA.getValue().doFilter(req, res);
    capturedChainB.getValue().doFilter(req, res);
    filterProxy.destroy();

    ems.verifyAll();
  }

  @Test
  public void postponedLoading() throws Exception {
    EasyMockSupport ems = new EasyMockSupport();

    FilterConfig config = ems.createMock(FilterConfig.class);
    HttpServletRequest req1 = new FakeHttpServletRequest();
    HttpServletRequest req2 = new FakeHttpServletRequest();
    HttpServletResponse res1 = new FakeHttpServletResponse();
    HttpServletResponse res2 = new FakeHttpServletResponse();

    IMocksControl mockControl = ems.createStrictControl();
    FilterChain chain = mockControl.createMock("chain", FilterChain.class);

    Capture<FilterChain> capturedChainA1 = new Capture<>();
    Capture<FilterChain> capturedChainA2 = new Capture<>();
    Capture<FilterChain> capturedChainB = new Capture<>();

    AllRequestFilter filterA = mockControl.createMock("filterA", AllRequestFilter.class);
    AllRequestFilter filterB = mockControl.createMock("filterB", AllRequestFilter.class);

    filterA.init(config);
    filterA.doFilter(eq(req1), eq(res1), capture(capturedChainA1));
    chain.doFilter(req1, res1);

    filterA.doFilter(eq(req2), eq(res2), capture(capturedChainA2));
    filterB.init(config); // <-- This is crucial part. filterB got loaded
    // after filterProxy's init finished. Nonetheless filterB gets initialized.
    filterB.doFilter(eq(req2), eq(res2), capture(capturedChainB));
    chain.doFilter(req2, res2);

    filterA.destroy();
    filterB.destroy();

    ems.replayAll();

    AllRequestFilter.FilterProxy filterProxy = getFilterProxy();
    addFilter(filterA);

    filterProxy.init(config);
    filterProxy.doFilter(req1, res1, chain);
    capturedChainA1.getValue().doFilter(req1, res1);

    addFilter(filterB); // <-- Adds filter after filterProxy's init got called.
    filterProxy.doFilter(req2, res2, chain);
    capturedChainA2.getValue().doFilter(req2, res2);
    capturedChainB.getValue().doFilter(req2, res2);

    filterProxy.destroy();

    ems.verifyAll();
  }

  @Test
  public void dynamicUnloading() throws Exception {
    EasyMockSupport ems = new EasyMockSupport();

    FilterConfig config = ems.createMock(FilterConfig.class);
    HttpServletRequest req1 = new FakeHttpServletRequest();
    HttpServletRequest req2 = new FakeHttpServletRequest();
    HttpServletRequest req3 = new FakeHttpServletRequest();
    HttpServletResponse res1 = new FakeHttpServletResponse();
    HttpServletResponse res2 = new FakeHttpServletResponse();
    HttpServletResponse res3 = new FakeHttpServletResponse();

    Plugin plugin = ems.createMock(Plugin.class);

    IMocksControl mockControl = ems.createStrictControl();
    FilterChain chain = mockControl.createMock("chain", FilterChain.class);

    Capture<FilterChain> capturedChainA1 = new Capture<>();
    Capture<FilterChain> capturedChainB1 = new Capture<>();
    Capture<FilterChain> capturedChainB2 = new Capture<>();

    AllRequestFilter filterA = mockControl.createMock("filterA", AllRequestFilter.class);
    AllRequestFilter filterB = mockControl.createMock("filterB", AllRequestFilter.class);

    filterA.init(config);
    filterB.init(config);

    filterA.doFilter(eq(req1), eq(res1), capture(capturedChainA1));
    filterB.doFilter(eq(req1), eq(res1), capture(capturedChainB1));
    chain.doFilter(req1, res1);

    filterA.destroy(); // Cleaning up of filterA after it got unloaded

    filterB.doFilter(eq(req2), eq(res2), capture(capturedChainB2));
    chain.doFilter(req2, res2);

    filterB.destroy(); // Cleaning up of filterA after it got unloaded

    chain.doFilter(req3, res3);

    ems.replayAll();

    AllRequestFilter.FilterProxy filterProxy = getFilterProxy();
    ReloadableRegistrationHandle<AllRequestFilter> handleFilterA = addFilter(filterA);
    ReloadableRegistrationHandle<AllRequestFilter> handleFilterB = addFilter(filterB);

    filterProxy.init(config);

    // Request #1 with filterA and filterB
    filterProxy.doFilter(req1, res1, chain);
    capturedChainA1.getValue().doFilter(req1, res1);
    capturedChainB1.getValue().doFilter(req1, res1);

    // Unloading filterA
    handleFilterA.remove();
    filterProxy.onStopPlugin(plugin);

    // Request #1 only with filterB
    filterProxy.doFilter(req2, res2, chain);
    capturedChainA1.getValue().doFilter(req2, res2);

    // Unloading filterB
    handleFilterB.remove();
    filterProxy.onStopPlugin(plugin);

    // Request #1 with no additional filters
    filterProxy.doFilter(req3, res3, chain);

    filterProxy.destroy();

    ems.verifyAll();
  }
}
