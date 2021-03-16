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
  USER_REFERRED_FROM = 'User referred from',
}

export enum Execution {
  PLUGIN_API = 'plugin-api',
  REACHABLE_CODE = 'reachable code',
  METHOD_USED = 'method used',
}

export enum Timing {
  APP_STARTED = 'App Started',
  CHANGE_DISPLAYED = 'ChangeDisplayed',
  CHANGE_LOAD_FULL = 'ChangeFullyLoaded',
  DASHBOARD_DISPLAYED = 'DashboardDisplayed',
  DIFF_VIEW_CONTENT_DISPLAYED = 'DiffViewOnlyContent',
  DIFF_VIEW_DISPLAYED = 'DiffViewDisplayed',
  DIFF_VIEW_LOAD_FULL = 'DiffViewFullyLoaded',
  FILE_LIST_DISPLAYED = 'FileListDisplayed',
  PLUGINS_LOADED = 'PluginsLoaded',
  STARTUP_CHANGE_DISPLAYED = 'StartupChangeDisplayed',
  STARTUP_CHANGE_LOAD_FULL = 'StartupChangeFullyLoaded',
  STARTUP_DASHBOARD_DISPLAYED = 'StartupDashboardDisplayed',
  STARTUP_DIFF_VIEW_CONTENT_DISPLAYED = 'StartupDiffViewOnlyContent',
  STARTUP_DIFF_VIEW_DISPLAYED = 'StartupDiffViewDisplayed',
  STARTUP_DIFF_VIEW_LOAD_FULL = 'StartupDiffViewFullyLoaded',
  STARTUP_FILE_LIST_DISPLAYED = 'StartupFileListDisplayed',
  WEB_COMPONENTS_READY = 'WebComponentsReady',
  METRICS_PLUGIN_LOADED = 'MetricsPluginLoaded',
  CHANGE_DATA = 'ChangeDataLoaded',
  CHANGE_RELOAD = 'ChangeReloaded',
  SEND_REPLY = 'SendReply',
  DIFF_TOTAL = 'Diff Total Render',
  DIFF_CONTENT = 'Diff Content Render',
  DIFF_SYNTAX = 'Diff Syntax Render',
  FILE_RENDER = 'FileListRenderTime',
  FILE_RENDER_AVG = 'FileListRenderTimePerFile',
  FILE_EXPAND_ALL = 'ExpandAllDiffs',
  FILE_EXPAND_ALL_AVG = 'ExpandAllPerDiff',
}
