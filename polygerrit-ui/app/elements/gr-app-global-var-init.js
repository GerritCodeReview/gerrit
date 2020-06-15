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

import {GrDisplayNameUtils} from '../scripts/gr-display-name-utils/gr-display-name-utils.js';
import {GrAnnotation} from './diff/gr-diff-highlight/gr-annotation.js';
import {GrAttributeHelper} from './plugins/gr-attribute-helper/gr-attribute-helper.js';
import {GrDiffLine} from './diff/gr-diff/gr-diff-line.js';
import {GrDiffGroup} from './diff/gr-diff/gr-diff-group.js';
import {GrDiffBuilder} from './diff/gr-diff-builder/gr-diff-builder.js';
import {GrDiffBuilderSideBySide} from './diff/gr-diff-builder/gr-diff-builder-side-by-side.js';
import {GrDiffBuilderImage} from './diff/gr-diff-builder/gr-diff-builder-image.js';
import {GrDiffBuilderUnified} from './diff/gr-diff-builder/gr-diff-builder-unified.js';
import {GrDiffBuilderBinary} from './diff/gr-diff-builder/gr-diff-builder-binary.js';
import {GrChangeActionsInterface} from './shared/gr-js-api-interface/gr-change-actions-js-api.js';
import {GrChangeReplyInterface} from './shared/gr-js-api-interface/gr-change-reply-js-api.js';
import {GrEditConstants} from './edit/gr-edit-constants.js';
import {GrFileListConstants} from './change/gr-file-list-constants.js';
import {GrDomHooksManager, GrDomHook} from './plugins/gr-dom-hooks/gr-dom-hooks.js';
import {GrEtagDecorator} from './shared/gr-rest-api-interface/gr-etag-decorator.js';
import {GrThemeApi} from './plugins/gr-theme-api/gr-theme-api.js';
import {SiteBasedCache, FetchPromisesCache, GrRestApiHelper} from './shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper.js';
import {GrLinkTextParser} from './shared/gr-linked-text/link-text-parser.js';
import {pluginEndpoints, GrPluginEndpoints} from './shared/gr-js-api-interface/gr-plugin-endpoints.js';
import {GrReviewerUpdatesParser} from './shared/gr-rest-api-interface/gr-reviewer-updates-parser.js';
import {GrPopupInterface} from './plugins/gr-popup-interface/gr-popup-interface.js';
import {GrRangeNormalizer} from './diff/gr-diff-highlight/gr-range-normalizer.js';
import {GrCountStringFormatter} from './shared/gr-count-string-formatter/gr-count-string-formatter.js';
import {GrReviewerSuggestionsProvider, SUGGESTIONS_PROVIDERS_USERS_TYPES} from '../scripts/gr-reviewer-suggestions-provider/gr-reviewer-suggestions-provider.js';
import {util} from '../scripts/util.js';
import page from 'page/page.mjs';
import {Auth} from './shared/gr-rest-api-interface/gr-auth.js';
import {appContext} from '../services/app-context.js';
import {GrAdminApi} from './plugins/gr-admin-api/gr-admin-api.js';
import {GrAnnotationActionsContext} from './shared/gr-js-api-interface/gr-annotation-actions-context.js';
import {GrAnnotationActionsInterface} from './shared/gr-js-api-interface/gr-annotation-actions-js-api.js';
import {GrChangeMetadataApi} from './plugins/gr-change-metadata-api/gr-change-metadata-api.js';
import {GrEmailSuggestionsProvider} from '../scripts/gr-email-suggestions-provider/gr-email-suggestions-provider.js';
import {GrGroupSuggestionsProvider} from '../scripts/gr-group-suggestions-provider/gr-group-suggestions-provider.js';
import {GrEventHelper} from './plugins/gr-event-helper/gr-event-helper.js';
import {GrPluginRestApi} from './shared/gr-js-api-interface/gr-plugin-rest-api.js';
import {GrRepoApi} from './plugins/gr-repo-api/gr-repo-api.js';
import {GrSettingsApi} from './plugins/gr-settings-api/gr-settings-api.js';
import {GrStylesApi} from './plugins/gr-styles-api/gr-styles-api.js';
import {pluginLoader, PluginLoader} from './shared/gr-js-api-interface/gr-plugin-loader.js';
import {GrPluginActionContext} from './shared/gr-js-api-interface/gr-plugin-action-context.js';
import {getBaseUrl, getPluginNameFromUrl, getRestAPI, PLUGIN_LOADING_TIMEOUT_MS, PRELOADED_PROTOCOL, send} from './shared/gr-js-api-interface/gr-api-utils.js';
import {GerritNav} from './core/gr-navigation/gr-navigation.js';
import {getRootElement} from '../scripts/rootElement.js';
import {rangesEqual} from './diff/gr-diff/gr-diff-utils.js';
import {RevisionInfo} from './shared/revision-info/revision-info.js';
import {CoverageType} from '../types/types.js';
import {_setHiddenScroll, getHiddenScroll} from '../scripts/hiddenscroll.js';

export function initGlobalVariables() {
  window.GrDisplayNameUtils = GrDisplayNameUtils;
  window.GrAnnotation = GrAnnotation;
  window.GrAttributeHelper = GrAttributeHelper;
  window.GrDiffLine = GrDiffLine;
  window.GrDiffGroup = GrDiffGroup;
  window.GrDiffBuilder = GrDiffBuilder;
  window.GrDiffBuilderSideBySide = GrDiffBuilderSideBySide;
  window.GrDiffBuilderImage = GrDiffBuilderImage;
  window.GrDiffBuilderUnified = GrDiffBuilderUnified;
  window.GrDiffBuilderBinary = GrDiffBuilderBinary;
  window.GrChangeActionsInterface = GrChangeActionsInterface;
  window.GrChangeReplyInterface = GrChangeReplyInterface;
  window.GrEditConstants = GrEditConstants;
  window.GrFileListConstants = GrFileListConstants;
  window.GrDomHooksManager = GrDomHooksManager;
  window.GrDomHook = GrDomHook;
  window.GrEtagDecorator = GrEtagDecorator;
  window.GrThemeApi = GrThemeApi;
  window.SiteBasedCache = SiteBasedCache;
  window.FetchPromisesCache = FetchPromisesCache;
  window.GrRestApiHelper = GrRestApiHelper;
  window.GrLinkTextParser = GrLinkTextParser;
  window.GrPluginEndpoints = GrPluginEndpoints;
  window.GrReviewerUpdatesParser = GrReviewerUpdatesParser;
  window.GrPopupInterface = GrPopupInterface;
  window.GrRangeNormalizer = GrRangeNormalizer;
  window.GrCountStringFormatter = GrCountStringFormatter;
  window.GrReviewerSuggestionsProvider = GrReviewerSuggestionsProvider;
  window.util = util;
  window.page = page;
  window.Auth = Auth;
  window.EventEmitter = appContext.gerritEventEmitter;
  window.GrAdminApi = GrAdminApi;
  window.GrAnnotationActionsContext = GrAnnotationActionsContext;
  window.GrAnnotationActionsInterface = GrAnnotationActionsInterface;
  window.GrChangeMetadataApi = GrChangeMetadataApi;
  window.GrEmailSuggestionsProvider = GrEmailSuggestionsProvider;
  window.GrGroupSuggestionsProvider = GrGroupSuggestionsProvider;
  window.GrEventHelper = GrEventHelper;
  window.GrPluginRestApi = GrPluginRestApi;
  window.GrRepoApi = GrRepoApi;
  window.GrSettingsApi = GrSettingsApi;
  window.GrStylesApi = GrStylesApi;
  window.PluginLoader = PluginLoader;
  window.GrPluginActionContext = GrPluginActionContext;

  window._apiUtils = {
    getPluginNameFromUrl,
    send,
    getRestAPI,
    getBaseUrl,
    PRELOADED_PROTOCOL,
    PLUGIN_LOADING_TIMEOUT_MS,
  };

  window.Gerrit = window.Gerrit || {};
  window.Gerrit.Nav = GerritNav;
  window.Gerrit.getRootElement = getRootElement;

  window.Gerrit._pluginLoader = pluginLoader;
  window.Gerrit._endpoints = pluginEndpoints;

  window.Gerrit.slotToContent = slot => slot;
  window.Gerrit.rangesEqual = rangesEqual;
  window.Gerrit.SUGGESTIONS_PROVIDERS_USERS_TYPES =
      SUGGESTIONS_PROVIDERS_USERS_TYPES;
  window.Gerrit.RevisionInfo = RevisionInfo;
  window.Gerrit.CoverageType = CoverageType;
  Object.defineProperty(window.Gerrit, 'hiddenscroll', {
    get: getHiddenScroll,
    set: _setHiddenScroll,
  });
}
