// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.plugins;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GerritServerFlavourTest {

  // --- EE8 Gerrit: lenient, unmarked plugins are treated as EE8 ---

  @Test
  public void ee8AcceptsUnmarkedPlugin() throws Exception {
    GerritServerFlavour.checkPluginFlavour("foo", null, GerritServerFlavour.EE8);
  }

  @Test
  public void ee8AcceptsEe8Plugin() throws Exception {
    GerritServerFlavour.checkPluginFlavour("foo", "ee8", GerritServerFlavour.EE8);
  }

  @Test
  public void ee8RejectsEe10Plugin() {
    InvalidPluginException e =
        assertThrows(
            InvalidPluginException.class,
            () -> GerritServerFlavour.checkPluginFlavour("foo", "ee10", GerritServerFlavour.EE8));
    assertThat(e).hasMessageThat().contains("Plugin 'foo' is built for flavour 'ee10'");
    assertThat(e).hasMessageThat().contains("this Gerrit instance is 'ee8'");
    assertThat(e).hasMessageThat().contains("dev-plugins.html");
  }

  // --- EE10 Gerrit: strict, requires an explicit Gerrit-Flavour: ee10 ---

  @Test
  public void ee10AcceptsEe10Plugin() throws Exception {
    GerritServerFlavour.checkPluginFlavour("foo", "ee10", GerritServerFlavour.EE10);
  }

  @Test
  public void ee10RejectsUnmarkedPlugin() {
    InvalidPluginException e =
        assertThrows(
            InvalidPluginException.class,
            () -> GerritServerFlavour.checkPluginFlavour("foo", null, GerritServerFlavour.EE10));
    assertThat(e).hasMessageThat().contains("Plugin 'foo' is built for flavour 'ee8'");
    assertThat(e).hasMessageThat().contains("this Gerrit instance is 'ee10'");
  }

  @Test
  public void ee10RejectsEe8Plugin() {
    assertThrows(
        InvalidPluginException.class,
        () -> GerritServerFlavour.checkPluginFlavour("foo", "ee8", GerritServerFlavour.EE10));
  }

  // --- "any": an audited servlet-neutral plugin loads under both flavours ---

  @Test
  public void anyLoadsUnderEe8() throws Exception {
    GerritServerFlavour.checkPluginFlavour("foo", "any", GerritServerFlavour.EE8);
  }

  @Test
  public void anyLoadsUnderEe10() throws Exception {
    GerritServerFlavour.checkPluginFlavour("foo", "any", GerritServerFlavour.EE10);
  }

  // --- marker parsing is trimmed and case-insensitive ---

  @Test
  public void markerIsTrimmedAndCaseInsensitive() throws Exception {
    GerritServerFlavour.checkPluginFlavour("foo", "  EE10 ", GerritServerFlavour.EE10);
    GerritServerFlavour.checkPluginFlavour("foo", "Ee8", GerritServerFlavour.EE8);
  }

  @Test
  public void markerValuesMatchEnum() {
    assertThat(GerritServerFlavour.EE8.marker()).isEqualTo("ee8");
    assertThat(GerritServerFlavour.EE10.marker()).isEqualTo("ee10");
    assertThat(GerritServerFlavour.ANY.marker()).isEqualTo("any");
  }

  @Test
  public void unknownMarkerIsRejected() {
    InvalidPluginException e =
        assertThrows(
            InvalidPluginException.class,
            () -> GerritServerFlavour.checkPluginFlavour("foo", "ee11", GerritServerFlavour.EE10));
    assertThat(e).hasMessageThat().contains("unknown Gerrit-Flavour 'ee11'");
  }
}
