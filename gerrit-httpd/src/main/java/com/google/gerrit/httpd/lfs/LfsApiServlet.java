package com.google.gerrit.httpd.lfs;

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lfs.lib.LargeFileRepository;
import org.eclipse.jgit.lfs.server.LfsProtocolServlet;

@Singleton
public class LfsApiServlet extends LfsProtocolServlet {
  private static final long serialVersionUID = 1L;

  public static final String URL_REGEX =
      "^(?:/a)?(?:/p/|/)(.*/(?:info/lfs/objects/batch))$";

  private final DynamicItem<LargeFileRepository> largeFileRepository;

  @Inject
  LfsApiServlet(DynamicItem<LargeFileRepository> largeFileRepository) {
    this.largeFileRepository = largeFileRepository;
  }

  @Override
  protected LargeFileRepository getLargeFileRepository() {
    return largeFileRepository.get();
  }
}
