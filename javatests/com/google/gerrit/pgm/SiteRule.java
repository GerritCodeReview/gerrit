package com.google.gerrit.pgm;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.truth.ExitCodeSubject.exitCode;

import com.google.gerrit.common.FileUtil;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.testing.TempFileUtil;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Ignore;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

@Ignore
public class SiteRule implements TestRule {
  private SitePaths site;

  public Path getSitePath() {
    return site.site_path;
  }

  public SitePaths getSitePaths() {
    return site;
  }

  @Override
  public Statement apply(final Statement base, final Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        setupSiteData();
        base.evaluate();
        site = null;
        TempFileUtil.cleanup();
      }
    };
  }

  private void setupSiteData() throws IOException {
    Path tempPath = TempFileUtil.createTempDirectory().toPath();
    site = new SitePaths(tempPath);
    assertThat(site.isNew).named("Test Site directory is empty").isTrue();
    FileUtil.mkdirsOrDie(site.etc_dir, "Failed to create");
  }

  public void initSite() throws Exception {
    Init init = new Init(site.site_path);
    int exitCode = init.main(new String[] {"--batch"});

    assertAbout(exitCode()).that(exitCode).isSuccessful();
  }
}
