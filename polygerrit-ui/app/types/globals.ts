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
import {ParsedJSON, ServerInfo, AccountInfo, GroupInfo} from './common';
import {HighlightJS} from './types';
import {GrAttributeHelper} from '../elements/plugins/gr-attribute-helper/gr-attribute-helper';
import {GrAnnotation} from '../elements/diff/gr-diff-highlight/gr-annotation';
import {
  GrDiffLine,
  GrDiffLineType,
} from '../elements/diff/gr-diff/gr-diff-line';
import {
  GrDiffGroup,
  GrDiffGroupType,
} from '../elements/diff/gr-diff/gr-diff-group';
import {GrDiffBuilder} from '../elements/diff/gr-diff-builder/gr-diff-builder';
import {GrDiffBuilderSideBySide} from '../elements/diff/gr-diff-builder/gr-diff-builder-side-by-side';
import {GrDiffBuilderImage} from '../elements/diff/gr-diff-builder/gr-diff-builder-image';
import {GrDiffBuilderUnified} from '../elements/diff/gr-diff-builder/gr-diff-builder-unified';
import {GrDiffBuilderBinary} from '../elements/diff/gr-diff-builder/gr-diff-builder-binary';
import {GrChangeActionsInterface} from '../elements/shared/gr-js-api-interface/gr-change-actions-js-api';
import {GrChangeReplyInterface} from '../elements/shared/gr-js-api-interface/gr-change-reply-js-api';
import {GrEditConstants} from '../elements/edit/gr-edit-constants';
import {
  GrDomHooksManager,
  GrDomHook,
} from '../elements/plugins/gr-dom-hooks/gr-dom-hooks';
import {GrEtagDecorator} from '../elements/shared/gr-rest-api-interface/gr-etag-decorator';
import {GrThemeApi} from '../elements/plugins/gr-theme-api/gr-theme-api';
import {
  SiteBasedCache,
  FetchPromisesCache,
  GrRestApiHelper,
} from '../elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {GrLinkTextParser} from '../elements/shared/gr-linked-text/link-text-parser';
import {GrPluginEndpoints} from '../elements/shared/gr-js-api-interface/gr-plugin-endpoints';
import {GrReviewerUpdatesParser} from '../elements/shared/gr-rest-api-interface/gr-reviewer-updates-parser';
import {GrPopupInterface} from '../elements/plugins/gr-popup-interface/gr-popup-interface';
import {GrCountStringFormatter} from '../elements/shared/gr-count-string-formatter/gr-count-string-formatter';
import {GrReviewerSuggestionsProvider} from '../scripts/gr-reviewer-suggestions-provider/gr-reviewer-suggestions-provider';
import {util} from '../scripts/util';
import {appContext} from '../services/app-context';
import {GrAdminApi} from '../elements/plugins/gr-admin-api/gr-admin-api';
import {GrAnnotationActionsContext} from '../elements/shared/gr-js-api-interface/gr-annotation-actions-context';
import {GrAnnotationActionsInterface} from '../elements/shared/gr-js-api-interface/gr-annotation-actions-js-api';
import {GrChangeMetadataApi} from '../elements/plugins/gr-change-metadata-api/gr-change-metadata-api';
import {GrEmailSuggestionsProvider} from '../scripts/gr-email-suggestions-provider/gr-email-suggestions-provider';
import {GrGroupSuggestionsProvider} from '../scripts/gr-group-suggestions-provider/gr-group-suggestions-provider';
import {GrEventHelper} from '../elements/plugins/gr-event-helper/gr-event-helper';
import {GrPluginRestApi} from '../elements/shared/gr-js-api-interface/gr-plugin-rest-api';
import {GrRepoApi} from '../elements/plugins/gr-repo-api/gr-repo-api';
import {GrSettingsApi} from '../elements/plugins/gr-settings-api/gr-settings-api';
import {GrStylesApi} from '../elements/plugins/gr-styles-api/gr-styles-api';
import {PluginLoader} from '../elements/shared/gr-js-api-interface/gr-plugin-loader';
import {GrPluginActionContext} from '../elements/shared/gr-js-api-interface/gr-plugin-action-context';
import {GerritGlobal} from '../elements/shared/gr-js-api-interface/gr-gerrit';

export {};

declare global {
  interface Window {
    CANONICAL_PATH?: string;
    INITIAL_DATA?: {[key: string]: ParsedJSON};
    ShadyCSS?: {
      getComputedStyleValue(el: Element, name: string): string;
    };
    ShadyDOM?: {
      inUse?: boolean;
    };
    HTMLImports?: {whenReady: (cb: () => void) => void};
    linkify(
      text: string,
      options: {callback: (text: string, href?: string) => void}
    ): void;
    ASSETS_PATH?: string;
    Gerrit: GerritGlobal;
    // TODO(TS): define polymer type
    Polymer?: unknown;
    // TODO(TS): remove page when better workaround is found
    // page shouldn't be exposed in window and it shouldn't be used
    // it's defined because of limitations from typescript, which don't import .mjs
    page?: unknown;
    hljs?: HighlightJS;

    DEFAULT_DETAIL_HEXES?: {
      diffPage?: string;
      changePage?: string;
      dashboardPage?: string;
    };
    STATIC_RESOURCE_PATH?: string;

    /** Enhancements on Gr elements or utils */
    // TODO(TS): should clean up those and removing them may break certain plugin behaviors
    // TODO(TS): eslint is reporting undef for following typeof, turn it off for now
    // as this is type enhancement only and most of them should be removed soon
    /* eslint-disable no-undef */
    GrDisplayNameUtils: {
      getUserName: (
        config?: ServerInfo | undefined,
        account?: AccountInfo | undefined
      ) => string;
      getDisplayName: (
        config?: ServerInfo | undefined,
        account?: AccountInfo | undefined,
        firstNameOnly?: boolean
      ) => string;
      getAccountDisplayName: (
        config: ServerInfo | undefined,
        account: AccountInfo
      ) => string;
      getGroupDisplayName: (group: GroupInfo) => string;
    };
    GrAnnotation: typeof GrAnnotation;
    GrAttributeHelper: typeof GrAttributeHelper;
    GrDiffLine: typeof GrDiffLine;
    GrDiffLineType: typeof GrDiffLineType;
    GrDiffGroup: typeof GrDiffGroup;
    GrDiffGroupType: typeof GrDiffGroupType;
    GrDiffBuilder: typeof GrDiffBuilder;
    GrDiffBuilderSideBySide: typeof GrDiffBuilderSideBySide;
    GrDiffBuilderImage: typeof GrDiffBuilderImage;
    GrDiffBuilderUnified: typeof GrDiffBuilderUnified;
    GrDiffBuilderBinary: typeof GrDiffBuilderBinary;
    GrChangeActionsInterface: typeof GrChangeActionsInterface;
    GrChangeReplyInterface: typeof GrChangeReplyInterface;
    GrEditConstants: typeof GrEditConstants;
    GrDomHooksManager: typeof GrDomHooksManager;
    GrDomHook: typeof GrDomHook;
    GrEtagDecorator: typeof GrEtagDecorator;
    GrThemeApi: typeof GrThemeApi;
    SiteBasedCache: typeof SiteBasedCache;
    FetchPromisesCache: typeof FetchPromisesCache;
    GrRestApiHelper: typeof GrRestApiHelper;
    GrLinkTextParser: typeof GrLinkTextParser;
    GrPluginEndpoints: typeof GrPluginEndpoints;
    GrReviewerUpdatesParser: typeof GrReviewerUpdatesParser;
    GrPopupInterface: typeof GrPopupInterface;
    GrCountStringFormatter: typeof GrCountStringFormatter;
    GrReviewerSuggestionsProvider: typeof GrReviewerSuggestionsProvider;
    util: typeof util;
    Auth: typeof appContext.authService;
    EventEmitter: typeof appContext.eventEmitter;
    GrAdminApi: typeof GrAdminApi;
    GrAnnotationActionsContext: typeof GrAnnotationActionsContext;
    GrAnnotationActionsInterface: typeof GrAnnotationActionsInterface;
    GrChangeMetadataApi: typeof GrChangeMetadataApi;
    GrEmailSuggestionsProvider: typeof GrEmailSuggestionsProvider;
    GrGroupSuggestionsProvider: typeof GrGroupSuggestionsProvider;
    GrEventHelper: typeof GrEventHelper;
    GrPluginRestApi: typeof GrPluginRestApi;
    GrRepoApi: typeof GrRepoApi;
    GrSettingsApi: typeof GrSettingsApi;
    GrStylesApi: typeof GrStylesApi;
    PluginLoader: typeof PluginLoader;
    GrPluginActionContext: typeof GrPluginActionContext;
    _apiUtils: {};
    /* eslint-enable no-undef */
  }

  interface Performance {
    // typescript doesn't know about the memory property.
    // Define it here, so it can be used everywhere
    memory?: {
      jsHeapSizeLimit: number;
      totalJSHeapSize: number;
      usedJSHeapSize: number;
    };
  }
}
