// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.config;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.server.config.UserPreferencesConverter.DiffPreferencesInfoConverter.DIFF_PREFERENCES_INFO_CONVERTER;
import static com.google.gerrit.server.config.UserPreferencesConverter.EditPreferencesInfoConverter.EDIT_PREFERENCES_INFO_CONVERTER;
import static com.google.gerrit.server.config.UserPreferencesConverter.GeneralPreferencesInfoConverter.GENERAL_PREFERENCES_INFO_CONVERTER;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.proto.Entities.UserPreferences;
import com.google.gerrit.proto.Entities.UserPreferences.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.proto.Entities.UserPreferences.GeneralPreferencesInfo.DateFormat;
import com.google.gerrit.proto.Entities.UserPreferences.GeneralPreferencesInfo.DefaultBase;
import com.google.gerrit.proto.Entities.UserPreferences.GeneralPreferencesInfo.DiffView;
import com.google.gerrit.proto.Entities.UserPreferences.GeneralPreferencesInfo.EmailFormat;
import com.google.gerrit.proto.Entities.UserPreferences.GeneralPreferencesInfo.EmailStrategy;
import com.google.gerrit.proto.Entities.UserPreferences.GeneralPreferencesInfo.MenuItem;
import com.google.gerrit.proto.Entities.UserPreferences.GeneralPreferencesInfo.Theme;
import com.google.gerrit.proto.Entities.UserPreferences.GeneralPreferencesInfo.TimeFormat;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import java.util.EnumSet;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UserPreferencesConverterTest {
  @Test
  public void generalPreferencesInfo_compareEnumNames() {
    // The converter assumes that the enum type equivalents have exactly the same values in both
    // classes. This test goes over all the enums to verify this assumption.
    //
    // If this test breaks, you are likely changing an enum. Please add it to the upstream Java
    // class first, and on import - also update the proto version and the converter.
    ImmutableMap<String, EnumDescriptor> protoEnums =
        getProtoEnum(UserPreferences.GeneralPreferencesInfo.getDescriptor());
    ImmutableMap<String, EnumSet<?>> javaEnums = getJavaEnums(GeneralPreferencesInfo.class);
    assertThat(protoEnums.keySet()).containsExactlyElementsIn(javaEnums.keySet());
    for (String enumName : protoEnums.keySet()) {
      ImmutableList<String> protoEnumValues =
          protoEnums.get(enumName).getValues().stream()
              .map(v -> v.getName())
              .collect(toImmutableList());
      ImmutableList<String> javaEnumValues =
          javaEnums.get(enumName).stream().map(Enum::name).collect(toImmutableList());
      assertThat(protoEnumValues).containsExactlyElementsIn(javaEnumValues);
    }
  }

  /**
   * If this test fails, it's likely that you added a field to {@link GeneralPreferencesInfo}, or
   * that you have changed the default value for such a field. Please update the {@link
   * com.google.gerrit.proto.Entities.UserPreferences.GeneralPreferencesInfo} proto accordingly.
   */
  @Test
  public void generalPreferencesInfo_javaDefaultsKeptOnDoubleConversion() {
    GeneralPreferencesInfo orig = GeneralPreferencesInfo.defaults();
    GeneralPreferencesInfo res =
        GENERAL_PREFERENCES_INFO_CONVERTER.fromProto(
            GENERAL_PREFERENCES_INFO_CONVERTER.toProto(orig));
    assertThat(res).isEqualTo(orig);
  }

  /**
   * If this test fails, it's likely that you added a field to {@link
   * com.google.gerrit.proto.Entities.UserPreferences.GeneralPreferencesInfo}, or that you have
   * changed the default value for such a field. Please update the {@link GeneralPreferencesInfo}
   * class accordingly.
   */
  @Test
  public void generalPreferencesInfo_protoDefaultsKeptOnDoubleConversion() {
    UserPreferences.GeneralPreferencesInfo orig =
        UserPreferences.GeneralPreferencesInfo.getDefaultInstance();
    UserPreferences.GeneralPreferencesInfo res =
        GENERAL_PREFERENCES_INFO_CONVERTER.toProto(
            GENERAL_PREFERENCES_INFO_CONVERTER.fromProto(orig));
    assertThat(res).isEqualTo(orig);
  }

  @Test
  public void generalPreferencesInfo_doubleConversionWithAllFieldsSet() {
    UserPreferences.GeneralPreferencesInfo originalProto =
        UserPreferences.GeneralPreferencesInfo.newBuilder()
            .setChangesPerPage(42)
            .setDownloadScheme("DownloadScheme")
            .setTheme(Theme.DARK)
            .setDateFormat(DateFormat.UK)
            .setTimeFormat(TimeFormat.HHMM_24)
            .setExpandInlineDiffs(true)
            .setRelativeDateInChangeTable(true)
            .setDiffView(DiffView.UNIFIED_DIFF)
            .setSizeBarInChangeTable(true)
            .setLegacycidInChangeTable(true)
            .setMuteCommonPathPrefixes(true)
            .setSignedOffBy(true)
            .setEmailStrategy(EmailStrategy.CC_ON_OWN_COMMENTS)
            .setEmailFormat(EmailFormat.HTML_PLAINTEXT)
            .setDefaultBaseForMerges(DefaultBase.FIRST_PARENT)
            .setPublishCommentsOnPush(true)
            .setDisableKeyboardShortcuts(true)
            .setDisableTokenHighlighting(true)
            .setWorkInProgressByDefault(true)
            .addAllMyMenuItems(
                ImmutableList.of(
                    MenuItem.newBuilder()
                        .setUrl("url1")
                        .setName("name1")
                        .setTarget("target1")
                        .setId("id1")
                        .build(),
                    MenuItem.newBuilder()
                        .setUrl("url2")
                        .setName("name2")
                        .setTarget("target2")
                        .setId("id2")
                        .build()))
            .addAllChangeTable(ImmutableList.of("table1", "table2"))
            .setAllowBrowserNotifications(true)
            .setDiffPageSidebar("plugin-insight")
            .build();
    UserPreferences.GeneralPreferencesInfo resProto =
        GENERAL_PREFERENCES_INFO_CONVERTER.toProto(
            GENERAL_PREFERENCES_INFO_CONVERTER.fromProto(originalProto));
    assertThat(resProto).isEqualTo(originalProto);
  }

  @Test
  public void generalPreferencesInfo_toProtoTrimsMyMenuSpaces() {
    GeneralPreferencesInfo info = new GeneralPreferencesInfo();
    info.my =
        ImmutableList.of(
            new com.google.gerrit.extensions.client.MenuItem(
                " name1 ", " url1 ", " target1 ", " id1 "),
            new com.google.gerrit.extensions.client.MenuItem(null, " url2 ", null, null));
    UserPreferences.GeneralPreferencesInfo resProto =
        GENERAL_PREFERENCES_INFO_CONVERTER.toProto(info);
    assertThat(resProto)
        .isEqualTo(
            UserPreferences.GeneralPreferencesInfo.newBuilder()
                .addAllMyMenuItems(
                    ImmutableList.of(
                        MenuItem.newBuilder()
                            .setUrl("url1")
                            .setName("name1")
                            .setTarget("target1")
                            .setId("id1")
                            .build(),
                        MenuItem.newBuilder().setUrl("url2").build()))
                .build());
  }

  @Test
  public void generalPreferencesInfo_fromProtoTrimsMyMenuSpaces() {
    UserPreferences.GeneralPreferencesInfo originalProto =
        UserPreferences.GeneralPreferencesInfo.newBuilder()
            .addAllMyMenuItems(
                ImmutableList.of(
                    MenuItem.newBuilder()
                        .setName(" name1 ")
                        .setUrl(" url1 ")
                        .setTarget(" target1 ")
                        .setId(" id1 ")
                        .build(),
                    MenuItem.newBuilder().setUrl(" url2 ").build()))
            .build();
    GeneralPreferencesInfo info = GENERAL_PREFERENCES_INFO_CONVERTER.fromProto(originalProto);
    assertThat(info.my)
        .containsExactly(
            new com.google.gerrit.extensions.client.MenuItem("name1", "url1", "target1", "id1"),
            new com.google.gerrit.extensions.client.MenuItem(null, "url2", null, null));
  }

  @Test
  public void generalPreferencesInfo_emptyJavaToProto() {
    GeneralPreferencesInfo info = new GeneralPreferencesInfo();
    UserPreferences.GeneralPreferencesInfo res = GENERAL_PREFERENCES_INFO_CONVERTER.toProto(info);
    assertThat(res).isEqualToDefaultInstance();
  }

  @Test
  public void generalPreferencesInfo_defaultJavaToProto() {
    GeneralPreferencesInfo info = GeneralPreferencesInfo.defaults();
    UserPreferences.GeneralPreferencesInfo res = GENERAL_PREFERENCES_INFO_CONVERTER.toProto(info);
    assertThat(res)
        .ignoringFieldAbsence()
        .isEqualTo(UserPreferences.GeneralPreferencesInfo.getDefaultInstance());
  }

  @Test
  public void generalPreferencesInfo_emptyProtoToJava() {
    UserPreferences.GeneralPreferencesInfo proto =
        UserPreferences.GeneralPreferencesInfo.getDefaultInstance();
    GeneralPreferencesInfo res = GENERAL_PREFERENCES_INFO_CONVERTER.fromProto(proto);
    assertThat(res).isEqualTo(new GeneralPreferencesInfo());
  }

  @Test
  public void diffPreferencesInfo_compareEnumNames() {
    // The converter assumes that the enum type equivalents have exactly the same values in both
    // classes. This test goes over all the enums to verify this assumption.
    //
    // If this test breaks, you are likely changing an enum. Please add it to the upstream Java
    // class first, and on import - also update the proto version and the converter.
    ImmutableMap<String, EnumDescriptor> protoEnums =
        getProtoEnum(UserPreferences.DiffPreferencesInfo.getDescriptor());
    ImmutableMap<String, EnumSet<?>> javaEnums = getJavaEnums(DiffPreferencesInfo.class);
    assertThat(protoEnums.keySet()).containsExactlyElementsIn(javaEnums.keySet());
    for (String enumName : protoEnums.keySet()) {
      ImmutableList<String> protoEnumValues =
          protoEnums.get(enumName).getValues().stream()
              .map(v -> v.getName())
              .collect(toImmutableList());
      ImmutableList<String> javaEnumValues =
          javaEnums.get(enumName).stream().map(Enum::name).collect(toImmutableList());
      assertThat(protoEnumValues).containsExactlyElementsIn(javaEnumValues);
    }
  }

  /**
   * If this test fails, it's likely that you added a field to {@link DiffPreferencesInfo}, or that
   * you have changed the default value for such a field. Please update the {@link
   * com.google.gerrit.proto.Entities.UserPreferences.DiffPreferencesInfo} proto accordingly.
   */
  @Test
  public void diffPreferencesInfo_javaDefaultsKeptOnDoubleConversion() {
    DiffPreferencesInfo orig = DiffPreferencesInfo.defaults();
    DiffPreferencesInfo res =
        DIFF_PREFERENCES_INFO_CONVERTER.fromProto(DIFF_PREFERENCES_INFO_CONVERTER.toProto(orig));
    assertThat(res).isEqualTo(orig);
  }

  /**
   * If this test fails, it's likely that you added a field to {@link
   * com.google.gerrit.proto.Entities.UserPreferences.DiffPreferencesInfo}, or that you have changed
   * the default value for such a field. Please update the {@link DiffPreferencesInfo} class
   * accordingly.
   */
  @Test
  public void diffPreferencesInfo_protoDefaultsKeptOnDoubleConversion() {
    UserPreferences.DiffPreferencesInfo orig =
        UserPreferences.DiffPreferencesInfo.getDefaultInstance();
    UserPreferences.DiffPreferencesInfo res =
        DIFF_PREFERENCES_INFO_CONVERTER.toProto(DIFF_PREFERENCES_INFO_CONVERTER.fromProto(orig));
    assertThat(res).isEqualTo(orig);
  }

  @Test
  public void diffPreferencesInfo_doubleConversionWithAllFieldsSet() {
    UserPreferences.DiffPreferencesInfo originalProto =
        UserPreferences.DiffPreferencesInfo.newBuilder()
            .setContext(1)
            .setTabSize(2)
            .setFontSize(3)
            .setLineLength(4)
            .setCursorBlinkRate(5)
            .setExpandAllComments(false)
            .setIntralineDifference(true)
            .setManualReview(false)
            .setShowLineEndings(true)
            .setShowTabs(false)
            .setShowWhitespaceErrors(true)
            .setSyntaxHighlighting(false)
            .setHideTopMenu(true)
            .setAutoHideDiffTableHeader(false)
            .setHideLineNumbers(true)
            .setRenderEntireFile(false)
            .setHideEmptyPane(true)
            .setMatchBrackets(false)
            .setLineWrapping(true)
            .setIgnoreWhitespace(Whitespace.IGNORE_TRAILING)
            .setRetainHeader(true)
            .setSkipDeleted(false)
            .setSkipUnchanged(true)
            .setSkipUncommented(false)
            .build();
    UserPreferences.DiffPreferencesInfo resProto =
        DIFF_PREFERENCES_INFO_CONVERTER.toProto(
            DIFF_PREFERENCES_INFO_CONVERTER.fromProto(originalProto));
    assertThat(resProto).isEqualTo(originalProto);
  }

  @Test
  public void diffPreferencesInfo_emptyJavaToProto() {
    DiffPreferencesInfo info = new DiffPreferencesInfo();
    UserPreferences.DiffPreferencesInfo res = DIFF_PREFERENCES_INFO_CONVERTER.toProto(info);
    assertThat(res).isEqualToDefaultInstance();
  }

  @Test
  public void diffPreferencesInfo_defaultJavaToProto() {
    DiffPreferencesInfo info = DiffPreferencesInfo.defaults();
    UserPreferences.DiffPreferencesInfo res = DIFF_PREFERENCES_INFO_CONVERTER.toProto(info);
    assertThat(res)
        .ignoringFieldAbsence()
        .isEqualTo(UserPreferences.DiffPreferencesInfo.getDefaultInstance());
  }

  @Test
  public void diffPreferencesInfo_emptyProtoToJava() {
    UserPreferences.DiffPreferencesInfo proto =
        UserPreferences.DiffPreferencesInfo.getDefaultInstance();
    DiffPreferencesInfo res = DIFF_PREFERENCES_INFO_CONVERTER.fromProto(proto);
    assertThat(res).isEqualTo(new DiffPreferencesInfo());
  }

  @Test
  public void editPreferencesInfo_compareEnumNames() {
    // The converter assumes that the enum type equivalents have exactly the same values in both
    // classes. This test goes over all the enums to verify this assumption.
    //
    // If this test breaks, you are likely changing an enum. Please add it to the upstream Java
    // class first, and on import - also update the proto version and the converter.
    ImmutableMap<String, EnumDescriptor> protoEnums =
        getProtoEnum(UserPreferences.EditPreferencesInfo.getDescriptor());
    ImmutableMap<String, EnumSet<?>> javaEnums = getJavaEnums(EditPreferencesInfo.class);
    assertThat(protoEnums.keySet()).containsExactlyElementsIn(javaEnums.keySet());
    for (String enumName : protoEnums.keySet()) {
      ImmutableList<String> protoEnumValues =
          protoEnums.get(enumName).getValues().stream()
              .map(v -> v.getName())
              .collect(toImmutableList());
      ImmutableList<String> javaEnumValues =
          javaEnums.get(enumName).stream().map(Enum::name).collect(toImmutableList());
      assertThat(protoEnumValues).containsExactlyElementsIn(javaEnumValues);
    }
  }

  /**
   * If this test fails, it's likely that you added a field to {@link EditPreferencesInfo}, or that
   * you have changed the default value for such a field. Please update the {@link
   * com.google.gerrit.proto.Entities.UserPreferences.EditPreferencesInfo} proto accordingly.
   */
  @Test
  public void editPreferencesInfo_javaDefaultsKeptOnDoubleConversion() {
    EditPreferencesInfo orig = EditPreferencesInfo.defaults();
    EditPreferencesInfo res =
        EDIT_PREFERENCES_INFO_CONVERTER.fromProto(EDIT_PREFERENCES_INFO_CONVERTER.toProto(orig));
    assertThat(res).isEqualTo(orig);
  }

  /**
   * If this test fails, it's likely that you added a field to {@link
   * com.google.gerrit.proto.Entities.UserPreferences.EditPreferencesInfo}, or that you have changed
   * the default value for such a field. Please update the {@link EditPreferencesInfo} class
   * accordingly.
   */
  @Test
  public void editPreferencesInfo_protoDefaultsKeptOnDoubleConversion() {
    UserPreferences.EditPreferencesInfo orig =
        UserPreferences.EditPreferencesInfo.getDefaultInstance();
    UserPreferences.EditPreferencesInfo res =
        EDIT_PREFERENCES_INFO_CONVERTER.toProto(EDIT_PREFERENCES_INFO_CONVERTER.fromProto(orig));
    assertThat(res).isEqualTo(orig);
  }

  @Test
  public void editPreferencesInfo_doubleConversionWithAllFieldsSet() {
    UserPreferences.EditPreferencesInfo originalProto =
        UserPreferences.EditPreferencesInfo.newBuilder()
            .setTabSize(2)
            .setLineLength(3)
            .setIndentUnit(5)
            .setCursorBlinkRate(7)
            .setHideTopMenu(true)
            .setShowTabs(false)
            .setShowWhitespaceErrors(true)
            .setSyntaxHighlighting(false)
            .setHideLineNumbers(true)
            .setMatchBrackets(false)
            .setLineWrapping(true)
            .setIndentWithTabs(false)
            .setAutoCloseBrackets(true)
            .setShowBase(false)
            .build();
    UserPreferences.EditPreferencesInfo resProto =
        EDIT_PREFERENCES_INFO_CONVERTER.toProto(
            EDIT_PREFERENCES_INFO_CONVERTER.fromProto(originalProto));
    assertThat(resProto).isEqualTo(originalProto);
  }

  @Test
  public void editPreferencesInfo_emptyJavaToProto() {
    EditPreferencesInfo info = new EditPreferencesInfo();
    UserPreferences.EditPreferencesInfo res = EDIT_PREFERENCES_INFO_CONVERTER.toProto(info);
    assertThat(res).isEqualToDefaultInstance();
  }

  @Test
  public void editPreferencesInfo_defaultJavaToProto() {
    EditPreferencesInfo info = EditPreferencesInfo.defaults();
    UserPreferences.EditPreferencesInfo res = EDIT_PREFERENCES_INFO_CONVERTER.toProto(info);
    assertThat(res)
        .ignoringFieldAbsence()
        .isEqualTo(UserPreferences.EditPreferencesInfo.getDefaultInstance());
  }

  @Test
  public void editPreferencesInfo_emptyProtoToJava() {
    UserPreferences.EditPreferencesInfo proto =
        UserPreferences.EditPreferencesInfo.getDefaultInstance();
    EditPreferencesInfo res = EDIT_PREFERENCES_INFO_CONVERTER.fromProto(proto);
    assertThat(res).isEqualTo(new EditPreferencesInfo());
  }

  private ImmutableMap<String, EnumDescriptor> getProtoEnum(Descriptor d) {
    return d.getEnumTypes().stream().collect(toImmutableMap(e -> e.getName(), Function.identity()));
  }

  @SuppressWarnings("unchecked")
  private ImmutableMap<String, EnumSet<?>> getJavaEnums(Class<?> c) {
    return stream(c.getDeclaredClasses())
        .filter(Class::isEnum)
        .collect(
            toImmutableMap(Class::getSimpleName, e -> EnumSet.allOf(e.asSubclass(Enum.class))));
  }
}
