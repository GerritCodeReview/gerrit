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
    Gerrit?: {
      Nav?: unknown;
      getRootElement?: unknown;
      Auth?: unknown;
      _pluginLoader?: unknown;
      _endpoints?: unknown;
      slotToContent?: unknown;
      rangesEqual?: unknown;
      SUGGESTIONS_PROVIDERS_USERS_TYPES?: unknown;
      RevisionInfo?: unknown;
      CoverageType?: unknown;
      hiddenscroll?: unknown;
      flushPreinstalls?: () => void;
    };
    // TODO(TS): define polymer type
    Polymer?: {importHref?: unknown};
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

    VERSION_INFO?: string;

    /** Enhancements on Gr elements or utils */
    // TODO(TS): should clean up those and removing them may break certain plugin behaviors
    // TODO(TS): as @brohlfs suggested, to avoid importing anything from elements/ to types/
    // use any for them for now
    GrDisplayNameUtils: unknown;
    GrAnnotation: unknown;
    GrAttributeHelper: unknown;
    GrDiffLine: unknown;
    GrDiffLineType: unknown;
    GrDiffGroup: unknown;
    GrDiffGroupType: unknown;
    GrDiffBuilder: unknown;
    GrDiffBuilderSideBySide: unknown;
    GrDiffBuilderImage: unknown;
    GrDiffBuilderUnified: unknown;
    GrDiffBuilderBinary: unknown;
    GrChangeActionsInterface: unknown;
    GrChangeReplyInterface: unknown;
    GrEditConstants: unknown;
    GrDomHooksManager: unknown;
    GrDomHook: unknown;
    GrEtagDecorator: unknown;
    GrThemeApi: unknown;
    SiteBasedCache: unknown;
    FetchPromisesCache: unknown;
    GrRestApiHelper: unknown;
    GrLinkTextParser: unknown;
    GrPluginEndpoints: unknown;
    GrReviewerUpdatesParser: unknown;
    GrPopupInterface: unknown;
    GrCountStringFormatter: unknown;
    GrReviewerSuggestionsProvider: unknown;
    util: unknown;
    Auth: unknown;
    EventEmitter: unknown;
    GrAdminApi: unknown;
    GrAnnotationActionsContext: unknown;
    GrAnnotationActionsInterface: unknown;
    GrChangeMetadataApi: unknown;
    GrEmailSuggestionsProvider: unknown;
    GrGroupSuggestionsProvider: unknown;
    GrEventHelper: unknown;
    GrPluginRestApi: unknown;
    GrRepoApi: unknown;
    GrSettingsApi: unknown;
    GrStylesApi: unknown;
    PluginLoader: unknown;
    GrPluginActionContext: unknown;
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

  interface Event {
    // path is a non-standard property. Actually, this is optional property,
    // but marking it as optional breaks CustomKeyboardEvent
    // TODO(TS): replace with composedPath if possible
    readonly path: EventTarget[];
  }

  interface Error {
    lineNumber?: number; // non-standard property
    columnNumber?: number; // non-standard property
  }
}
