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
import {ParsedJSON} from './common';
import {HighlightJS} from './types';

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
    // TODO(TS): define gerrit type
    Gerrit?: any;
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

    PRELOADED_QUERIES?: {
      dashboardQuery?: string[];
    };

    /** Enhancements on Gr elements or utils */
    // TODO(TS): should clean up those and removing them may break certain plugin behaviors
    // TODO(TS): as @brohlfs suggested, to avoid importing anything from elements/ to types/
    // use any for them for now
    GrDisplayNameUtils: any;
    GrAnnotation: any;
    GrAttributeHelper: any;
    GrDiffLine: any;
    GrDiffLineType: any;
    GrDiffGroup: any;
    GrDiffGroupType: any;
    GrDiffBuilder: any;
    GrDiffBuilderSideBySide: any;
    GrDiffBuilderImage: any;
    GrDiffBuilderUnified: any;
    GrDiffBuilderBinary: any;
    GrChangeActionsInterface: any;
    GrChangeReplyInterface: any;
    GrEditConstants: any;
    GrDomHooksManager: any;
    GrDomHook: any;
    GrEtagDecorator: any;
    GrThemeApi: any;
    SiteBasedCache: any;
    FetchPromisesCache: any;
    GrRestApiHelper: any;
    GrLinkTextParser: any;
    GrPluginEndpoints: any;
    GrReviewerUpdatesParser: any;
    GrPopupInterface: any;
    GrCountStringFormatter: any;
    GrReviewerSuggestionsProvider: any;
    util: any;
    Auth: any;
    EventEmitter: any;
    GrAdminApi: any;
    GrAnnotationActionsContext: any;
    GrAnnotationActionsInterface: any;
    GrChangeMetadataApi: any;
    GrEmailSuggestionsProvider: any;
    GrGroupSuggestionsProvider: any;
    GrEventHelper: any;
    GrPluginRestApi: any;
    GrRepoApi: any;
    GrSettingsApi: any;
    GrStylesApi: any;
    PluginLoader: any;
    GrPluginActionContext: any;
    _apiUtils: {};
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
