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
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.eq;

import com.google.gerrit.extensions.registration.DynamicSet;

import org.easymock.Capture;
import org.easymock.EasyMockSupport;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class AllRequestFilterFilterProxyTest {
  /**
   * Set of filters for FilterProxy
   * <p/>
   * This set is used to as set of filters when fetching an
   * {@link AllRequestFilter.FilterProxy} instance through
   * {@link #getFilterProxy()}.
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
   * <p/>
   * The returned {@link AllRequestFilter.FilterProxy} can have new filters
   * added dynamically by calling {@link #addFilter(AllRequestFilter)}.
   */
  private AllRequestFilter.FilterProxy getFilterProxy() {
    return new AllRequestFilter.FilterProxy(filters);
  }

  /**
   * Add a filter to created FilterProxy instances
   * <p/>
   * This method add the gived filter to all
   * {@link AllRequestFilter.FilterProxy} instances created by
   * {@link #getFilterProxy()}.
   */
  private void addFilter(final AllRequestFilter filter) {
    filters.add(filter);
  }

  @Test
  public void testNoFilters() throws Exception {
    EasyMockSupport ems = new EasyMockSupport();

    FilterConfig config = ems.createMock(FilterConfig.class);
    HttpServletRequest req = ems.createMock(HttpServletRequest.class);
    HttpServletResponse res = ems.createMock(HttpServletResponse.class);

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
  public void testSingleFilterNoBubbling() throws Exception {
    EasyMockSupport ems = new EasyMockSupport();

    FilterConfig config = ems.createMock("config", FilterConfig.class);
    HttpServletRequest req = ems.createMock("req", HttpServletRequest.class);
    HttpServletResponse res = ems.createMock("res", HttpServletResponse.class);

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
  public void testSingleFilterBubbling() throws Exception {
    EasyMockSupport ems = new EasyMockSupport();

    FilterConfig config = ems.createMock(FilterConfig.class);
    HttpServletRequest req = ems.createMock(HttpServletRequest.class);
    HttpServletResponse res = ems.createMock(HttpServletResponse.class);

    IMocksControl mockControl = ems.createStrictControl();
    FilterChain chain = mockControl.createMock(FilterChain.class);

    Capture<FilterChain> capturedChain = newCapture();

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
  public void testTwoFiltersNoBubbling() throws Exception {
    EasyMockSupport ems = new EasyMockSupport();

    FilterConfig config = ems.createMock(FilterConfig.class);
    HttpServletRequest req = ems.createMock(HttpServletRequest.class);
    HttpServletResponse res = ems.createMock(HttpServletResponse.class);

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
  public void testTwoFiltersBubbling() throws Exception {
    EasyMockSupport ems = new EasyMockSupport();

    FilterConfig config = ems.createMock(FilterConfig.class);
    HttpServletRequest req = ems.createMock(HttpServletRequest.class);
    HttpServletResponse res = ems.createMock(HttpServletResponse.class);

    IMocksControl mockControl = ems.createStrictControl();
    FilterChain chain = mockControl.createMock(FilterChain.class);

    Capture<FilterChain> capturedChainA = newCapture();
    Capture<FilterChain> capturedChainB = newCapture();

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
}
