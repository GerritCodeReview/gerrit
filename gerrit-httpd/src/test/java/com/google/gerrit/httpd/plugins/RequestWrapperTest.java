package com.google.gerrit.httpd.plugins;

import static org.junit.Assert.*;
import static org.easymock.EasyMock.*;

import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

public class RequestWrapperTest {

  private static final String CONTEXT = "https://foo.bar:443";
  private static final String PLUGIN_NAME = "my-plugin";
  private static final String RESOURCE = "my-resource";

  @Test
  public void testUnautorized() throws Exception {
    RequestWrapper classUnderTest = new RequestWrapper(CONTEXT);

    HttpServletRequest originalRequest =
        createMockRequest("/plugins/", PLUGIN_NAME + "/" + RESOURCE);

    HttpServletRequest result =
        classUnderTest.create(originalRequest, PLUGIN_NAME);

    assertEquals(CONTEXT + "/plugins/" + PLUGIN_NAME, result.getContextPath());
    assertEquals("/", result.getServletPath());
    assertEquals(RESOURCE, result.getPathInfo());
    assertEquals(CONTEXT + "/plugins/" + (PLUGIN_NAME + "/" + RESOURCE),
        result.getRequestURI());
  }

  @Test
  public void testAutorized() throws Exception {
    RequestWrapper classUnderTest = new RequestWrapper(CONTEXT);

    HttpServletRequest originalRequest =
        createMockRequest("/a/plugins/", PLUGIN_NAME + "/" + RESOURCE);

    HttpServletRequest result =
        classUnderTest.create(originalRequest, PLUGIN_NAME);

    assertEquals(CONTEXT + "/a/plugins/" + PLUGIN_NAME,
        result.getContextPath());
    assertEquals("/", result.getServletPath());
    assertEquals(RESOURCE, result.getPathInfo());
    assertEquals(CONTEXT + "/a/plugins/" + (PLUGIN_NAME + "/" + RESOURCE),
        result.getRequestURI());
  }

  private static HttpServletRequest createMockRequest(String servletPath,
      String pathInfo) {
    HttpServletRequest req = createNiceMock(HttpServletRequest.class);
    expect(req.getContextPath()).andStubReturn(CONTEXT);
    expect(req.getServletPath()).andStubReturn(servletPath);
    expect(req.getPathInfo()).andStubReturn(pathInfo);
    String uri = CONTEXT + servletPath + pathInfo;
    expect(req.getRequestURI()).andStubReturn(uri);
    replay(req);

    return req;
  }

}
