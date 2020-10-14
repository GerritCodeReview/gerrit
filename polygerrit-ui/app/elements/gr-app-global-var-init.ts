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

import {
  getAccountDisplayName,
  getDisplayName,
  getGroupDisplayName,
  getUserName,
} from '../utils/display-name-util';
import {GrAnnotation} from './diff/gr-diff-highlight/gr-annotation';
import {GrAttributeHelper} from './plugins/gr-attribute-helper/gr-attribute-helper';
import {GrDiffLine, GrDiffLineType} from './diff/gr-diff/gr-diff-line';
import {GrDiffGroup, GrDiffGroupType} from './diff/gr-diff/gr-diff-group';
import {GrDiffBuilder} from './diff/gr-diff-builder/gr-diff-builder';
import {GrDiffBuilderSideBySide} from './diff/gr-diff-builder/gr-diff-builder-side-by-side';
import {GrDiffBuilderImage} from './diff/gr-diff-builder/gr-diff-builder-image';
import {GrDiffBuilderUnified} from './diff/gr-diff-builder/gr-diff-builder-unified';
import {GrDiffBuilderBinary} from './diff/gr-diff-builder/gr-diff-builder-binary';
import {GrChangeActionsInterface} from './shared/gr-js-api-interface/gr-change-actions-js-api';
import {GrChangeReplyInterface} from './shared/gr-js-api-interface/gr-change-reply-js-api';
import {GrEditConstants} from './edit/gr-edit-constants';
import {
  GrDomHooksManager,
  GrDomHook,
} from './plugins/gr-dom-hooks/gr-dom-hooks';
import {GrEtagDecorator} from './shared/gr-rest-api-interface/gr-etag-decorator';
import {GrThemeApi} from './plugins/gr-theme-api/gr-theme-api';
import {
  SiteBasedCache,
  FetchPromisesCache,
  GrRestApiHelper,
} from './shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {GrLinkTextParser} from './shared/gr-linked-text/link-text-parser';
import {
  getPluginEndpoints,
  GrPluginEndpoints,
} from './shared/gr-js-api-interface/gr-plugin-endpoints';
import {GrReviewerUpdatesParser} from './shared/gr-rest-api-interface/gr-reviewer-updates-parser';
import {GrPopupInterface} from './plugins/gr-popup-interface/gr-popup-interface';
import {GrCountStringFormatter} from './shared/gr-count-string-formatter/gr-count-string-formatter';
import {
  GrReviewerSuggestionsProvider,
  SUGGESTIONS_PROVIDERS_USERS_TYPES,
} from '../scripts/gr-reviewer-suggestions-provider/gr-reviewer-suggestions-provider';
import {util} from '../scripts/util';
import {page} from '../utils/page-wrapper-utils';
import {appContext} from '../services/app-context';
import {GrAdminApi} from './plugins/gr-admin-api/gr-admin-api';
import {GrAnnotationActionsContext} from './shared/gr-js-api-interface/gr-annotation-actions-context';
import {GrAnnotationActionsInterface} from './shared/gr-js-api-interface/gr-annotation-actions-js-api';
import {GrChangeMetadataApi} from './plugins/gr-change-metadata-api/gr-change-metadata-api';
import {GrEmailSuggestionsProvider} from '../scripts/gr-email-suggestions-provider/gr-email-suggestions-provider';
import {GrGroupSuggestionsProvider} from '../scripts/gr-group-suggestions-provider/gr-group-suggestions-provider';
import {GrEventHelper} from './plugins/gr-event-helper/gr-event-helper';
import {GrPluginRestApi} from './shared/gr-js-api-interface/gr-plugin-rest-api';
import {GrRepoApi} from './plugins/gr-repo-api/gr-repo-api';
import {GrSettingsApi} from './plugins/gr-settings-api/gr-settings-api';
import {GrStylesApi} from './plugins/gr-styles-api/gr-styles-api';
import {
  getPluginLoader,
  PluginLoader,
} from './shared/gr-js-api-interface/gr-plugin-loader';
import {GrPluginActionContext} from './shared/gr-js-api-interface/gr-plugin-action-context';
import {
  getPluginNameFromUrl,
  getRestAPI,
  PLUGIN_LOADING_TIMEOUT_MS,
  PRELOADED_PROTOCOL,
  send,
} from './shared/gr-js-api-interface/gr-api-utils';
import {getBaseUrl} from '../utils/url-util';
import {GerritNav} from './core/gr-navigation/gr-navigation';
import {getRootElement} from '../scripts/rootElement';
import {rangesEqual} from './diff/gr-diff/gr-diff-utils';
import {RevisionInfo} from './shared/revision-info/revision-info';
import {CoverageType} from '../types/types';
import {_setHiddenScroll, getHiddenScroll} from '../scripts/hiddenscroll';

export function initGlobalVariables() {
  window.GrDisplayNameUtils = {
    getUserName,
    getDisplayName,
    getAccountDisplayName,
    getGroupDisplayName,
  };
  window.GrAnnotation = GrAnnotation;
  window.GrAttributeHelper = GrAttributeHelper;
  window.GrDiffLine = GrDiffLine;
  window.GrDiffLineType = GrDiffLineType;
  window.GrDiffGroup = GrDiffGroup;
  window.GrDiffGroupType = GrDiffGroupType;
  window.GrDiffBuilder = GrDiffBuilder;
  window.GrDiffBuilderSideBySide = GrDiffBuilderSideBySide;
  window.GrDiffBuilderImage = GrDiffBuilderImage;
  window.GrDiffBuilderUnified = GrDiffBuilderUnified;
  window.GrDiffBuilderBinary = GrDiffBuilderBinary;
  window.GrChangeActionsInterface = GrChangeActionsInterface;
  window.GrChangeReplyInterface = GrChangeReplyInterface;
  window.GrEditConstants = GrEditConstants;
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
  window.GrCountStringFormatter = GrCountStringFormatter;
  window.GrReviewerSuggestionsProvider = GrReviewerSuggestionsProvider;
  window.util = util;
  window.page = page;
  window.Auth = appContext.authService;
  window.EventEmitter = appContext.eventEmitter;
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
  window.Gerrit.Auth = appContext.authService;

  window.Gerrit._pluginLoader = getPluginLoader();
  // TODO: should define as a getter
  window.Gerrit._endpoints = getPluginEndpoints();

  // TODO(TS): seems not used, probably just remove
  window.Gerrit.slotToContent = (slot: any) => slot;
  window.Gerrit.rangesEqual = rangesEqual;
  window.Gerrit.SUGGESTIONS_PROVIDERS_USERS_TYPES = SUGGESTIONS_PROVIDERS_USERS_TYPES;
  window.Gerrit.RevisionInfo = RevisionInfo;
  window.Gerrit.CoverageType = CoverageType;
  Object.defineProperty(window.Gerrit, 'hiddenscroll', {
    get: getHiddenScroll,
    set: _setHiddenScroll,
  });
}
