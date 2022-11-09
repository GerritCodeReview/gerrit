package com.google.gerrit.httpd.raw;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.experiments.ExperimentFeaturesConstants.GERRIT_BACKEND_FEATURE_ATTACH_NONCE_TO_DOCUMENTATION;
import static org.mockito.Mockito.when;

import com.google.common.base.CharMatcher;
import com.google.common.cache.CacheBuilder;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.gerrit.server.experiments.ExperimentFeatures;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.gerrit.util.http.testutil.FakeHttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class DocServletTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private ExperimentFeatures experimentFeatures;
  private FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
  private DocServlet docServlet;

  @Before
  public void setUp() throws Exception {
    when(experimentFeatures.isFeatureEnabled(GERRIT_BACKEND_FEATURE_ATTACH_NONCE_TO_DOCUMENTATION))
        .thenReturn(true);

    docServlet =
        new DocServlet(
            CacheBuilder.newBuilder().maximumSize(1).build(), false, experimentFeatures) {
          @Override
          protected Path getResourcePath(String pathInfo) throws IOException {
            return fs.getPath("/" + CharMatcher.is('/').trimLeadingFrom(pathInfo));
          }
        };

    Files.createDirectories(fs.getPath(DOC_PATH).getParent());
    Files.write(fs.getPath(DOC_PATH), HTML_RESPONSE.getBytes(StandardCharsets.UTF_8));
    Files.write(
        fs.getPath(DOC_PATH_NO_SCRIPT), HTML_RESPONSE_NO_SCRIPT.getBytes(StandardCharsets.UTF_8));
    Files.write(fs.getPath(NON_HTML_FILE_PATH), NON_HTML_FILE);
  }

  @Test
  public void noNonce_unchangedResponse() throws Exception {
    FakeHttpServletRequest request = new FakeHttpServletRequest().setPathInfo(DOC_PATH);
    FakeHttpServletResponse response = new FakeHttpServletResponse();

    docServlet.doGet(request, response);

    assertThat(response.getActualBody()).isEqualTo(HTML_RESPONSE.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void experimentDisabled_unchangedResponse() throws Exception {
    when(experimentFeatures.isFeatureEnabled(GERRIT_BACKEND_FEATURE_ATTACH_NONCE_TO_DOCUMENTATION))
        .thenReturn(false);
    FakeHttpServletRequest request = new FakeHttpServletRequest().setPathInfo(DOC_PATH);
    request.setAttribute("nonce", NONCE);
    FakeHttpServletResponse response = new FakeHttpServletResponse();

    docServlet.doGet(request, response);

    assertThat(response.getActualBody()).isEqualTo(HTML_RESPONSE.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void nonHtmlResponse_unchangedResponse() throws Exception {
    FakeHttpServletRequest request = new FakeHttpServletRequest().setPathInfo(NON_HTML_FILE_PATH);
    request.setAttribute("nonce", NONCE);
    FakeHttpServletResponse response = new FakeHttpServletResponse();

    docServlet.doGet(request, response);

    assertThat(response.getActualBody()).isEqualTo(NON_HTML_FILE);
  }

  @Test
  public void responseWithoutScripts_equivalentResponse() throws Exception {
    FakeHttpServletRequest request = new FakeHttpServletRequest().setPathInfo(DOC_PATH_NO_SCRIPT);
    request.setAttribute("nonce", NONCE);
    FakeHttpServletResponse response = new FakeHttpServletResponse();

    docServlet.doGet(request, response);

    // Normally file is not guaranteed to not get reformatted, but in the simple example like we use
    // here we can check byte-wise equality.
    assertThat(response.getActualBody())
        .isEqualTo(HTML_RESPONSE_NO_SCRIPT.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void htmlResponse_nonceAttached() throws Exception {
    FakeHttpServletRequest request = new FakeHttpServletRequest().setPathInfo(DOC_PATH);
    request.setAttribute("nonce", NONCE);
    FakeHttpServletResponse response = new FakeHttpServletResponse();

    docServlet.doGet(request, response);

    Document doc = Jsoup.parse(response.getActualBodyString());
    for (Element el : doc.getElementsByTag("script")) {
      assertThat(el.attributes().get("nonce")).isEqualTo(NONCE);
    }
  }

  @Test
  public void htmlResponse_noCacheHeaderSet() throws Exception {
    FakeHttpServletRequest request = new FakeHttpServletRequest().setPathInfo(DOC_PATH);
    request.setAttribute("nonce", NONCE);
    FakeHttpServletResponse response = new FakeHttpServletResponse();

    docServlet.doGet(request, response);

    assertThat(response.getHeader("Cache-Control"))
        .isEqualTo("no-cache, no-store, max-age=0, must-revalidate");
  }

  private static final String NONCE = "1234abcde";
  private static final String HTML_RESPONSE =
      "<!DOCTYPE html>"
          + "<html lang=\"en\">"
          + "<head>"
          + "  <title>Gerrit Code Review - Searching Changes</title>"
          + "  <link rel=\"stylesheet\" href=\"./asciidoctor.css\">"
          + "  <script src=\"./prettify.min.js\"></script>"
          + "  <script>document.addEventListener('DOMContentLoaded', prettyPrint)</script>"
          + "</head><body></body></html>";
  private static final String DOC_PATH = "/Documentation/page1.html";
  private static final String HTML_RESPONSE_NO_SCRIPT =
      "<html><head></head><body><div>Hello</div></body></html>";
  private static final String DOC_PATH_NO_SCRIPT = "/Documentation/page_no_script.html";
  private static final byte[] NON_HTML_FILE = "<script></script>".getBytes(StandardCharsets.UTF_8);
  private static final String NON_HTML_FILE_PATH = "/foo";
}
