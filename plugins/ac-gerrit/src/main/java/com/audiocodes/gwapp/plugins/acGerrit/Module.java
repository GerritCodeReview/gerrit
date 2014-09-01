package com.audiocodes.gwapp.plugins.acGerrit;

import static com.google.gerrit.server.change.RevisionResource.REVISION_KIND;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.git.validators.MergeValidationListener;
import com.google.inject.AbstractModule;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Module extends AbstractModule {

  @Override
  protected void configure() {
    String hostName = "";
    try {
      hostName = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
    }
    final boolean isCloud = !hostName.contains("rebaser");
    DynamicSet.bind(binder(), MergeValidationListener.class)
        .to(isCloud ? MergeVIValidator.class : MergeUDRValidator.class);
    DynamicSet.bind(binder(), MergeValidationListener.class).to(MergeSubmoduleValidator.class);
    install(
        new RestApiModule() {
          @Override
          protected void configure() {
            post(REVISION_KIND, "build").to(BuildAction.class);
            post(REVISION_KIND, "sanity").to(SanityAction.class);
            post(REVISION_KIND, "full-sanity").to(FullSanityAction.class);
            post(REVISION_KIND, "submit-with-super").to(SubmitWithSuperAction.class);
          }
        });
  }
}
