package com.google.gerrit.reviewdb.client;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DateFormat;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DefaultBase;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DiffView;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DownloadCommand;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailFormat;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.ReviewCategoryStrategy;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.TimeFormat;
import com.google.gerrit.extensions.client.MenuItem;
import java.util.Optional;

@AutoValue
public abstract class Preferences {

  @AutoValue
  public static abstract class General {
    public abstract Optional<Integer> changesPerPage();
    public abstract Optional< Boolean> showSiteHeader();
    public abstract Optional< Boolean> useFlashClipboard();
    public abstract Optional< String> downloadScheme();
    public abstract Optional< DownloadCommand> downloadCommand();
    public abstract Optional< DateFormat> dateFormat();
    public abstract Optional< TimeFormat> timeFormat();
    public abstract Optional< Boolean> expandInlineDiffs();
    public abstract Optional< Boolean> highlightAssigneeInChangeTable();
    public abstract Optional< Boolean> relativeDateInChangeTable();
    public abstract Optional< DiffView> diffView();
    public abstract Optional< Boolean >sizeBarInChangeTable();
    public abstract Optional< Boolean> legacycidInChangeTable();
    public abstract Optional< ReviewCategoryStrategy >reviewCategoryStrategy();
    public abstract Optional< Boolean> muteCommonPathPrefixes();
    public abstract Optional< Boolean >signedOffBy();
    public abstract Optional< EmailStrategy >emailStrategy();
    public abstract Optional< EmailFormat> emailFormat();
    public abstract Optional< DefaultBase >defaultBaseForMerges();
    public abstract Optional< Boolean> publishCommentsOnPush();
    public abstract Optional< Boolean >workInProgressByDefault();
    public abstract Optional<ImmutableList<MenuItem>>my();
    public abstract Optional< ImmutableList<String> >changeTable();
    public abstract Optional<ImmutableMap<String, String>>urlAliases();

    @AutoValue.Builder
    public abstract static class Builder {
      abstract Builder changesPerPage(@Nullable Integer val);
      abstract Builder showSiteHeader(Boolean val);
      abstract Builder useFlashClipboard(Boolean val);
      abstract Builder downloadScheme(String val);
      abstract Builder downloadCommand(DownloadCommand val);
      abstract Builder dateFormat(DateFormat val);
      abstract Builder timeFormat(TimeFormat val);
      abstract Builder expandInlineDiffs(Boolean val);
      abstract Builder highlightAssigneeInChangeTable(Boolean val);
      abstract Builder relativeDateInChangeTable(Boolean val);
      abstract Builder diffView(DiffView val);
      abstract Builder sizeBarInChangeTable(Boolean val);
      abstract Builder legacycidInChangeTable(Boolean val);
      abstract Builder reviewCategoryStrategy(ReviewCategoryStrategy val);
      abstract Builder muteCommonPathPrefixes(Boolean val);
      abstract Builder signedOffBy(Boolean val);
      abstract Builder emailStrategy(EmailStrategy val);
      abstract Builder emailFormat(EmailFormat val);
      abstract Builder defaultBaseForMerges(DefaultBase val);
      abstract Builder publishCommentsOnPush(Boolean val);
      abstract Builder workInProgressByDefault(Boolean val);
      abstract Builder my(ImmutableList<MenuItem> val);
      abstract Builder changeTable(ImmutableList<String> val);
      abstract Builder urlAliases(ImmutableMap<String, String> val);

      abstract General build();
    }

    public static General fromInfo(GeneralPreferencesInfo info) {
      return (new AutoValue_Preferences_General.Builder())
          .changesPerPage(info.changesPerPage)
          .showSiteHeader(info.showSiteHeader)
          .useFlashClipboard(info.useFlashClipboard)
          .downloadScheme(info.downloadScheme)
          .downloadCommand(info.downloadCommand)
          .dateFormat(info.dateFormat)
          .timeFormat(info.timeFormat)
          .expandInlineDiffs(info.expandInlineDiffs)
          .highlightAssigneeInChangeTable(info.highlightAssigneeInChangeTable)
          .relativeDateInChangeTable(info.relativeDateInChangeTable)
          .diffView(info.diffView)
          .sizeBarInChangeTable(info.sizeBarInChangeTable)
          .legacycidInChangeTable(info.legacycidInChangeTable)
          .reviewCategoryStrategy(info.reviewCategoryStrategy)
          .muteCommonPathPrefixes(info.muteCommonPathPrefixes)
          .signedOffBy(info.signedOffBy)
          .emailStrategy(info.emailStrategy)
          .emailFormat(info.emailFormat)
          .defaultBaseForMerges(info.defaultBaseForMerges)
          .publishCommentsOnPush(info.publishCommentsOnPush)
          .workInProgressByDefault(info.workInProgressByDefault)
          .my(ImmutableList.copyOf(info.my))
          .changeTable(ImmutableList.copyOf(info.changeTable))
          .urlAliases(ImmutableMap.copyOf(info.urlAliases))
          .build();
    }

    public GeneralPreferencesInfo toInfo() {
      GeneralPreferencesInfo info = new GeneralPreferencesInfo();
      info.changesPerPage = changesPerPage().orElse(null);
      info.showSiteHeader = showSiteHeader().orElse(null);
      info.useFlashClipboard = useFlashClipboard().orElse(null);
      info.downloadScheme = downloadScheme().orElse(null);
      info.downloadCommand = downloadCommand().orElse(null);
      info.dateFormat = dateFormat().orElse(null);
      info.timeFormat = timeFormat().orElse(null);
      info.expandInlineDiffs = expandInlineDiffs().orElse(null);
      info.highlightAssigneeInChangeTable = highlightAssigneeInChangeTable().orElse(null);
      info.relativeDateInChangeTable = relativeDateInChangeTable().orElse(null);
      info.diffView = diffView().orElse(null);
      info.sizeBarInChangeTable = sizeBarInChangeTable().orElse(null);
      info.legacycidInChangeTable = legacycidInChangeTable().orElse(null);
      info.reviewCategoryStrategy = reviewCategoryStrategy().orElse(null);
      info.muteCommonPathPrefixes = muteCommonPathPrefixes().orElse(null);
      info.signedOffBy = signedOffBy().orElse(null);
      info.emailStrategy = emailStrategy().orElse(null);
      info.emailFormat = emailFormat().orElse(null);
      info.defaultBaseForMerges = defaultBaseForMerges().orElse(null);
      info.publishCommentsOnPush = publishCommentsOnPush().orElse(null);
      info.workInProgressByDefault = workInProgressByDefault().orElse(null);
      info.my = my().orElse(null);
      info.changeTable = changeTable().orElse(null);
      info.urlAliases = urlAliases().orElse(null);
      return info;
    }
  }
}
