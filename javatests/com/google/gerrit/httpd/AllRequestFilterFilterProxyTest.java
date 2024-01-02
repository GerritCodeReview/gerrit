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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
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
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

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
  @CanIgnoreReturnValue
  private ReloadableRegistrationHandle<AllRequestFilter> addFilter(AllRequestFilter filter) {
    Key<AllRequestFilter> key = Key.get(AllRequestFilter.class);
    return filters.add("gerrit", key, Providers.of(filter));
  }

  @Test
  public void noFilters() throws Exception {
    FilterConfig config = mock(FilterConfig.class);
    HttpServletRequest req = new FakeHttpServletRequest();
    HttpServletResponse res = new FakeHttpServletResponse();

    FilterChain chain = mock(FilterChain.class);

    AllRequestFilter.FilterProxy filterProxy = getFilterProxy();

    filterProxy.init(config);
    filterProxy.doFilter(req, res, chain);
    filterProxy.destroy();

    verify(chain).doFilter(req, res);
  }

  @Test
  public void singleFilterNoBubbling() throws Exception {
    FilterConfig config = mock(FilterConfig.class);
    HttpServletRequest req = new FakeHttpServletRequest();
    HttpServletResponse res = new FakeHttpServletResponse();

    FilterChain chain = mock(FilterChain.class);

    AllRequestFilter filter = mock(AllRequestFilter.class);

    AllRequestFilter.FilterProxy filterProxy = getFilterProxy();
    addFilter(filter);

    filterProxy.init(config);
    filterProxy.doFilter(req, res, chain);
    filterProxy.destroy();

    InOrder inorder = inOrder(filter);
    inorder.verify(filter).init(config);
    inorder.verify(filter).doFilter(eq(req), eq(res), any(FilterChain.class));
    inorder.verify(filter).destroy();
  }

  @Test
  public void singleFilterBubbling() throws Exception {
    FilterConfig config = mock(FilterConfig.class);
    HttpServletRequest req = new FakeHttpServletRequest();
    HttpServletResponse res = new FakeHttpServletResponse();

    FilterChain chain = mock(FilterChain.class);

    ArgumentCaptor<FilterChain> capturedChain = ArgumentCaptor.forClass(FilterChain.class);

    AllRequestFilter filter = mock(AllRequestFilter.class);

    AllRequestFilter.FilterProxy filterProxy = getFilterProxy();
    addFilter(filter);

    InOrder inorder = inOrder(filter, chain);

    filterProxy.init(config);
    filterProxy.doFilter(req, res, chain);

    inorder.verify(filter).init(config);
    inorder.verify(filter).doFilter(eq(req), eq(res), capturedChain.capture());
    capturedChain.getValue().doFilter(req, res);
    inorder.verify(chain).doFilter(req, res);

    filterProxy.destroy();
    inorder.verify(filter).destroy();
  }

  @Test
  public void twoFiltersNoBubbling() throws Exception {
    FilterConfig config = mock(FilterConfig.class);
    HttpServletRequest req = new FakeHttpServletRequest();
    HttpServletResponse res = new FakeHttpServletResponse();

    FilterChain chain = mock(FilterChain.class);

    AllRequestFilter filterA = mock(AllRequestFilter.class);

    AllRequestFilter filterB = mock(AllRequestFilter.class);
    AllRequestFilter.FilterProxy filterProxy = getFilterProxy();
    addFilter(filterA);
    addFilter(filterB);

    filterProxy.init(config);
    filterProxy.doFilter(req, res, chain);
    filterProxy.destroy();

    InOrder inorder = inOrder(filterA, filterB);
    inorder.verify(filterA).init(config);
    inorder.verify(filterB).init(config);
    inorder.verify(filterA).doFilter(eq(req), eq(res), any(FilterChain.class));
    inorder.verify(filterA).destroy();
    inorder.verify(filterB).destroy();
  }

  @Test
  public void twoFiltersBubbling() throws Exception {
    FilterConfig config = mock(FilterConfig.class);
    HttpServletRequest req = new FakeHttpServletRequest();
    HttpServletResponse res = new FakeHttpServletResponse();

    FilterChain chain = mock(FilterChain.class);

    ArgumentCaptor<FilterChain> capturedChainA = ArgumentCaptor.forClass(FilterChain.class);
    ArgumentCaptor<FilterChain> capturedChainB = ArgumentCaptor.forClass(FilterChain.class);

    AllRequestFilter filterA = mock(AllRequestFilter.class);
    AllRequestFilter filterB = mock(AllRequestFilter.class);

    AllRequestFilter.FilterProxy filterProxy = getFilterProxy();
    addFilter(filterA);
    addFilter(filterB);

    filterProxy.init(config);
    filterProxy.doFilter(req, res, chain);

    InOrder inorder = inOrder(filterA, filterB, chain);

    inorder.verify(filterA).init(config);
    inorder.verify(filterB).init(config);
    inorder.verify(filterA).doFilter(eq(req), eq(res), capturedChainA.capture());
    capturedChainA.getValue().doFilter(req, res);
    inorder.verify(filterB).doFilter(eq(req), eq(res), capturedChainB.capture());
    capturedChainB.getValue().doFilter(req, res);
    inorder.verify(chain).doFilter(req, res);

    filterProxy.destroy();
    inorder.verify(filterA).destroy();
    inorder.verify(filterB).destroy();
  }

  @Test
  public void postponedLoading() throws Exception {
    FilterConfig config = mock(FilterConfig.class);
    HttpServletRequest req1 = new FakeHttpServletRequest();
    HttpServletRequest req2 = new FakeHttpServletRequest();
    HttpServletResponse res1 = new FakeHttpServletResponse();
    HttpServletResponse res2 = new FakeHttpServletResponse();

    FilterChain chain = mock(FilterChain.class);

    ArgumentCaptor<FilterChain> capturedChainA1 = ArgumentCaptor.forClass(FilterChain.class);
    ArgumentCaptor<FilterChain> capturedChainA2 = ArgumentCaptor.forClass(FilterChain.class);
    ArgumentCaptor<FilterChain> capturedChainB = ArgumentCaptor.forClass(FilterChain.class);

    AllRequestFilter filterA = mock(AllRequestFilter.class);
    AllRequestFilter filterB = mock(AllRequestFilter.class);

    InOrder inorder = inOrder(filterA, filterB, chain);

    AllRequestFilter.FilterProxy filterProxy = getFilterProxy();
    addFilter(filterA);

    filterProxy.init(config);
    filterProxy.doFilter(req1, res1, chain);
    inorder.verify(filterA).init(config);
    inorder.verify(filterA).doFilter(eq(req1), eq(res1), capturedChainA1.capture());
    capturedChainA1.getValue().doFilter(req1, res1);
    inorder.verify(chain).doFilter(req1, res1);

    addFilter(filterB); // <-- Adds filter after filterProxy's init got called.
    filterProxy.doFilter(req2, res2, chain);
    // after filterProxy's init finished. Nonetheless filterB gets initialized.
    inorder.verify(filterA).doFilter(eq(req2), eq(res2), capturedChainA2.capture());
    capturedChainA2.getValue().doFilter(req2, res2);
    inorder.verify(filterB).init(config); // <-- This is crucial part. filterB got loaded
    inorder.verify(filterB).doFilter(eq(req2), eq(res2), capturedChainB.capture());
    capturedChainB.getValue().doFilter(req2, res2);
    inorder.verify(chain).doFilter(req2, res2);

    filterProxy.destroy();
    inorder.verify(filterA).destroy();
    inorder.verify(filterB).destroy();
  }

  @Test
  public void dynamicUnloading() throws Exception {
    FilterConfig config = mock(FilterConfig.class);
    HttpServletRequest req1 = new FakeHttpServletRequest();
    HttpServletRequest req2 = new FakeHttpServletRequest();
    HttpServletRequest req3 = new FakeHttpServletRequest();
    HttpServletResponse res1 = new FakeHttpServletResponse();
    HttpServletResponse res2 = new FakeHttpServletResponse();
    HttpServletResponse res3 = new FakeHttpServletResponse();

    Plugin plugin = mock(Plugin.class);

    FilterChain chain = mock(FilterChain.class);

    ArgumentCaptor<FilterChain> capturedChainA1 = ArgumentCaptor.forClass(FilterChain.class);
    ArgumentCaptor<FilterChain> capturedChainB1 = ArgumentCaptor.forClass(FilterChain.class);
    ArgumentCaptor<FilterChain> capturedChainB2 = ArgumentCaptor.forClass(FilterChain.class);

    AllRequestFilter filterA = mock(AllRequestFilter.class);
    AllRequestFilter filterB = mock(AllRequestFilter.class);

    AllRequestFilter.FilterProxy filterProxy = getFilterProxy();
    ReloadableRegistrationHandle<AllRequestFilter> handleFilterA = addFilter(filterA);
    ReloadableRegistrationHandle<AllRequestFilter> handleFilterB = addFilter(filterB);

    InOrder inorder = inOrder(filterA, filterB, chain);

    filterProxy.init(config);

    inorder.verify(filterA).init(config);
    inorder.verify(filterB).init(config);

    // Request #1 with filterA and filterB
    filterProxy.doFilter(req1, res1, chain);
    inorder.verify(filterA).doFilter(eq(req1), eq(res1), capturedChainA1.capture());
    capturedChainA1.getValue().doFilter(req1, res1);
    inorder.verify(filterB).doFilter(eq(req1), eq(res1), capturedChainB1.capture());
    capturedChainB1.getValue().doFilter(req1, res1);
    inorder.verify(chain).doFilter(req1, res1);

    // Unloading filterA
    handleFilterA.remove();
    filterProxy.onStopPlugin(plugin);

    inorder.verify(filterA).destroy(); // Cleaning up of filterA after it got unloaded

    // Request #2 only with filterB
    filterProxy.doFilter(req2, res2, chain);

    inorder.verify(filterB).doFilter(eq(req2), eq(res2), capturedChainB2.capture());
    inorder.verify(filterA, never()).doFilter(eq(req2), eq(res2), any(FilterChain.class));
    capturedChainB2.getValue().doFilter(req2, res2);
    inorder.verify(chain).doFilter(req2, res2);

    // Unloading filterB
    handleFilterB.remove();
    filterProxy.onStopPlugin(plugin);

    inorder.verify(filterB).destroy(); // Cleaning up of filterA after it got unloaded

    // Request #3 with no additional filters
    filterProxy.doFilter(req3, res3, chain);
    inorder.verify(chain).doFilter(req3, res3);
    inorder.verify(filterA, never()).doFilter(eq(req2), eq(res2), any(FilterChain.class));
    inorder.verify(filterB, never()).doFilter(eq(req2), eq(res2), any(FilterChain.class));

    filterProxy.destroy();
  }
}
