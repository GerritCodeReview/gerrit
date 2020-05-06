package com.google.gerrit.acceptance.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import org.junit.Test;

public class GerritInstanceIdTest extends AbstractDaemonTest {

  @Test
  @GerritConfig(name = "gerrit.instanceId", value = "testInstanceId")
  public void getInstanceIdWhenDefined() {
    String ident = instanceId.get();
    assertThat(ident).isEqualTo("testInstanceId");
  }

  @Test
  public void getNullWhenNotDefined() {
    String ident = instanceId.get();
    assertThat(ident).isNull();
  }
}
