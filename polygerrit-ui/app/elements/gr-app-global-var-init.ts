/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @fileoverview This file is a backwards-compatibility shim.
 * Before Polygerrit converted to ES Modules, it exposes some variables out onto
 * the global namespace. Plugins can depend on these variables and we must
 * expose these variables until plugins switch to direct import from polygerrit.
 */

import {GrAnnotation} from './diff/gr-diff-highlight/gr-annotation';
import {GrDiffLine, GrDiffLineType} from './diff/gr-diff/gr-diff-line';
import {GrDiffGroup, GrDiffGroupType} from './diff/gr-diff/gr-diff-group';
import {getPluginEndpoints} from './shared/gr-js-api-interface/gr-plugin-endpoints';
import {util} from '../scripts/util';
import {page} from '../utils/page-wrapper-utils';
import {appContext} from '../services/app-context';
import {
  getPluginLoader,
  PluginLoader,
} from './shared/gr-js-api-interface/gr-plugin-loader';
import {GrPluginActionContext} from './shared/gr-js-api-interface/gr-plugin-action-context';
import {
  getPluginNameFromUrl,
  PLUGIN_LOADING_TIMEOUT_MS,
  PRELOADED_PROTOCOL,
  send,
} from './shared/gr-js-api-interface/gr-api-utils';
import {getBaseUrl} from '../utils/url-util';
import {GerritNav} from './core/gr-navigation/gr-navigation';
import {getRootElement} from '../scripts/rootElement';
import {RevisionInfo} from './shared/revision-info/revision-info';

export function initGlobalVariables() {
  window.GrAnnotation = GrAnnotation;
  window.GrDiffLine = GrDiffLine;
  window.GrDiffLineType = GrDiffLineType;
  window.GrDiffGroup = GrDiffGroup;
  window.GrDiffGroupType = GrDiffGroupType;
  window.util = util;
  window.page = page;
  window.Auth = appContext.authService;
  window.EventEmitter = appContext.eventEmitter;
  window.PluginLoader = PluginLoader;
  window.GrPluginActionContext = GrPluginActionContext;

  window._apiUtils = {
    getPluginNameFromUrl,
    send,
    getBaseUrl,
    PRELOADED_PROTOCOL,
    PLUGIN_LOADING_TIMEOUT_MS,
  };

  window.Gerrit = window.Gerrit || {};
  window.Gerrit.Nav = GerritNav;
  window.Gerrit.getRootElement = getRootElement;
  window.Gerrit.Auth = appContext.authService;

  window.Gerrit._pluginLoader = getPluginLoader();
  // TODO: should define as a getter
  window.Gerrit._endpoints = getPluginEndpoints();

  window.Gerrit.RevisionInfo = RevisionInfo;
}
