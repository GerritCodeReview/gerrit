/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http =//www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

export enum LifeCycle {
  PLUGIN_LIFE_CYCLE = 'Plugin life cycle',
  STARTED_AS_USER = 'Started as user',
  STARTED_AS_GUEST = 'Started as guest',
  VISIBILILITY_HIDDEN = 'Visibility changed to hidden',
  VISIBILILITY_VISIBLE = 'Visibility changed to visible',
  EXTENSION_DETECTED = 'Extension detected',
  PLUGINS_INSTALLED = 'Plugins installed',
  PLUGINS_FAILED = 'Some plugins failed to load',
  USER_REFERRED_FROM = 'User referred from',
}

export enum Execution {
  PLUGIN_API = 'plugin-api',
  REACHABLE_CODE = 'reachable code',
  METHOD_USED = 'method used',
  CHECKS_API_NOT_LOGGED_IN = 'checks-api not-logged-in',
  CHECKS_API_ERROR = 'checks-api error',
}

export enum Timing {
  // Time between the navigationStart timing and the ready call of gr-app.
  APP_STARTED = 'App Started',
  // Time from navigation to showing first content of change view.
  CHANGE_DISPLAYED = 'ChangeDisplayed',
  // Time from navigation to having loaded and presented all change data.
  CHANGE_LOAD_FULL = 'ChangeFullyLoaded',
  // Time from navigation to showing content of dashboard.
  DASHBOARD_DISPLAYED = 'DashboardDisplayed',
  // Time from navigation to showing full content of diff without highlighting layer
  DIFF_VIEW_CONTENT_DISPLAYED = 'DiffViewOnlyContent',
  // Time from navigation to showing full content of diff
  DIFF_VIEW_DISPLAYED = 'DiffViewDisplayed',
  // Time from navigation to showing initial content of the file list.
  FILE_LIST_DISPLAYED = 'FileListDisplayed',
  // Time from startup to having loaded all plugins.
  PLUGINS_LOADED = 'PluginsLoaded',
  // Time from startup to having loaded metrics plugin
  METRICS_PLUGIN_LOADED = 'MetricsPluginLoaded',
  // Time from startup to showing first content of change view.
  STARTUP_CHANGE_DISPLAYED = 'StartupChangeDisplayed',
  // Time from startup to having loaded and presented all change data.
  STARTUP_CHANGE_LOAD_FULL = 'StartupChangeFullyLoaded',
  // Time from startup to showing content of the dashboard.
  STARTUP_DASHBOARD_DISPLAYED = 'StartupDashboardDisplayed',
  // Time from startup to showing full content of diff without highlighting layer
  STARTUP_DIFF_VIEW_CONTENT_DISPLAYED = 'StartupDiffViewOnlyContent',
  // Time from startup to showing full content of diff view.
  STARTUP_DIFF_VIEW_DISPLAYED = 'StartupDiffViewDisplayed',
  // Time from startup to showing initial content of the file list.
  STARTUP_FILE_LIST_DISPLAYED = 'StartupFileListDisplayed',
  // Time from startup to when the webcomponentsready event is fired. If the event is fired from the webcomponents-lite polyfill, this may be arbitrarily long after the app has started.
  WEB_COMPONENTS_READY = 'WebComponentsReady',
  // Time to received all data for change view
  CHANGE_DATA = 'ChangeDataLoaded',
  // Time to compute and render first content of change view
  CHANGE_RELOAD = 'ChangeReloaded',
  // Time from clicking the [Send] button of the Reply Dialog to the time that the change has reloaded (core data)
  SEND_REPLY = 'SendReply',
  // The overall time to render a diff (excluding loading of data).
  DIFF_TOTAL = 'Diff Total Render',
  // The time to render the content off a diff (excluding loading of data or syntax highlighting).
  DIFF_CONTENT = 'Diff Content Render',
  // Time to compute syntax highlighting of a diff  minus diff rendering time (DIFF_CONTENT).
  DIFF_SYNTAX = 'Diff Syntax Render',
  // Time to load diff and prepare before gr-diff rendering begins.
  DIFF_LOAD = 'Diff Load Render',
  // Time to render a batch of rows in the file list. If there are very many files, this may be the first batch of rows that are rendered by default. If there are many files and the user clicks [Show More], this may be the batch of additional files that appear as a result.
  FILE_RENDER = 'FileListRenderTime',
  // The time to expand some number of diffs in the file list (i.e. render their diffs, including syntax highlighting).
  FILE_EXPAND_ALL = 'ExpandAllDiffs',
  // Time for making the REST API call of creating a draft comment.
  DRAFT_CREATE = 'CreateDraftComment',
  // Time for making the REST API call of update a draft comment.
  DRAFT_UPDATE = 'UpdateDraftComment',
  // Time for making the REST API call of deleting a draft comment.
  DRAFT_DISCARD = 'DiscardDraftComment',
  // Time to load checks from all providers for the first time.
  CHECKS_LOAD = 'ChecksLoad',
}

export enum Interaction {
  TOGGLE_SHOW_ALL_BUTTON = 'toggle show all button',
  SHOW_TAB = 'show-tab',
  ATTENTION_SET_CHIP = 'attention-set-chip',
  SAVE_COMMENT = 'save-comment',
  COMMENT_SAVED = 'comment-saved',
  DISCARD_COMMENT = 'discard-comment',
  COMMENT_DISCARDED = 'comment-discarded',
  ROBOT_COMMENTS_STATS = 'robot-comments-stats',
  CHECKS_TAB_RENDERED = 'checks-tab-rendered',
  CHECKS_CHIP_CLICKED = 'checks-chip-clicked',
  CHECKS_CHIP_LINK_CLICKED = 'checks-chip-link-clicked',
  CHECKS_RESULT_ROW_TOGGLE = 'checks-result-row-toggle',
  CHECKS_ACTION_TRIGGERED = 'checks-action-triggered',
  CHECKS_TAG_CLICKED = 'checks-tag-clicked',
  CHECKS_RESULT_FILTER_CHANGED = 'checks-result-filter-changed',
  CHECKS_RESULT_SECTION_TOGGLE = 'checks-result-section-toggle',
  CHECKS_RESULT_SECTION_SHOW_ALL = 'checks-result-section-show-all',
  CHECKS_RUN_SELECTED = 'checks-run-selected',
  CHECKS_RUN_LINK_CLICKED = 'checks-run-link-clicked',
  CHECKS_RUN_FILTER_CHANGED = 'checks-run-filter-changed',
  CHECKS_RUN_SECTION_TOGGLE = 'checks-run-section-toggle',
  CHECKS_ATTEMPT_SELECTED = 'checks-attempt-selected',
  CHECKS_RUNS_PANEL_TOGGLE = 'checks-runs-panel-toggle',
  CHECKS_RUNS_SELECTED_TRIGGERED = 'checks-runs-selected-triggered',
  CHECKS_STATS = 'checks-stats',
}
