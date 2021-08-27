package com.google.gerrit.server.config;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import java.util.Optional;
import javax.inject.Singleton;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

@Singleton
public class FileBasedAllProjectsConfigProvider implements AllProjectsConfigProvider {
  private final SitePaths sitePaths;

  @VisibleForTesting
  @Inject
  public FileBasedAllProjectsConfigProvider(SitePaths sitePaths) {
    this.sitePaths = sitePaths;
  }

  @Override
  public Optional<StoredConfig> get(AllProjectsName allProjectsName) {
    return Optional.of(
        new FileBasedConfig(
            sitePaths
                .etc_dir
                .resolve(allProjectsName.get())
                .resolve(ProjectConfig.PROJECT_CONFIG)
                .toFile(),
            FS.DETECTED));
  }
}
