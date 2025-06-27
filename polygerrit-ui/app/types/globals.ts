/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {MdElevatedButton} from '@material/web/button/elevated-button';
import {MdTextButton} from '@material/web/button/text-button';
import {MdCheckbox} from '@material/web/checkbox/checkbox';
import {MdFab} from '@material/web/fab/fab';
import {MdIcon} from '@material/web/icon/icon';
import {MdIconButton} from '@material/web/iconbutton/icon-button';
import {MdFilledCard} from '@material/web/labs/card/filled-card';
import {MdFilledSelect} from '@material/web/select/filled-select';
import {MdSelectOption} from '@material/web/select/select-option';
import {MdSwitch} from '@material/web/switch/switch';
import {MdSecondaryTab} from '@material/web/tabs/secondary-tab';
import {MdTabs} from '@material/web/tabs/tabs';
import {ParsedJSON} from './common';
import {HighlightJS} from './types';

export {};

declare global {
  interface HTMLElementTagNameMap {
    'md-elevated-button': MdElevatedButton;
    'md-text-button': MdTextButton;
    'md-checkbox': MdCheckbox;
    'md-fab': MdFab;
    'md-icon': MdIcon;
    'md-icon-button': MdIconButton;
    'md-filled-card': MdFilledCard;
    'md-filled-select': MdFilledSelect;
    'md-select-option': MdSelectOption;
    'md-switch': MdSwitch;
    'md-tabs': MdTabs;
    'md-secondary-tab': MdSecondaryTab;
  }

  interface Window {
    CANONICAL_PATH?: string;
    INITIAL_DATA?: {[key: string]: ParsedJSON};
    HTMLImports?: {whenReady: (cb: () => void) => void};
    linkify(
      text: string,
      options: {callback: (text: string, href?: string) => void}
    ): void;
    ASSETS_PATH?: string;
    // TODO(TS): remove page when better workaround is found
    // page shouldn't be exposed in window and it shouldn't be used
    // it's defined because of limitations from typescript, which don't import .mjs
    page?: unknown;
    hljs?: HighlightJS;
    emojis?: unknown;

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
    // Heads up! There is a known plugin dependency on GrPluginActionContext.
    GrPluginActionContext: unknown;
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

  interface Error {
    lineNumber?: number; // non-standard property
    columnNumber?: number; // non-standard property
  }

  interface ShadowRoot {
    getSelection?: () => Selection | null;
  }
}
