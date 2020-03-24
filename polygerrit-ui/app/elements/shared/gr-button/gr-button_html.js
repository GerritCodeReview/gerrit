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
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

export const htmlTemplate = html`
    <style include="shared-styles">
      /* general styles for all buttons */
      :host {
        --background-color: var(--button-background-color, var(--default-button-background-color));
        --text-color: var(--default-button-text-color);
        display: inline-block;
        position: relative;
      }
      :host([hidden]) {
        display: none;
      }
      :host([no-uppercase]) paper-button {
        text-transform: none;
      }
      paper-button {
        /* The next lines contains a copy of paper-button style.
          Without a copy, the @apply works incorrectly with Polymer 2.
          @apply is deprecated and is not recommended to use. It is expected
          that @apply will be replaced with the ::part CSS pseudo-element.
          After replacecment copied lines can be removed.
        */
        @apply --layout-inline;
        @apply --layout-center-center;
        position: relative;
        box-sizing: border-box;
        min-width: 5.14em;
        margin: 0 0.29em;
        background: transparent;
        -webkit-tap-highlight-color: rgba(0, 0, 0, 0);
        -webkit-tap-highlight-color: transparent;
        font: inherit;
        text-transform: uppercase;
        outline-width: 0;
        border-radius: var(--border-radius);
        -moz-user-select: none;
        -ms-user-select: none;
        -webkit-user-select: none;
        user-select: none;
        cursor: pointer;
        z-index: 0;
        padding: var(--spacing-m);

        @apply --paper-font-common-base;
        @apply --paper-button;
        /* End of copy*/

        /* paper-button sets this to anti-aliased, which appears different than
          bold font elsewhere on macOS. */
        -webkit-font-smoothing: initial;
        align-items: center;
        background-color: var(--background-color);
        color: var(--text-color);
        display: flex;
        font-family: inherit;
        justify-content: center;
        margin: var(--margin, 0);
        min-width: var(--border, 0);
        padding: var(--padding, 4px 8px);
        @apply --gr-button;
      }
      /* https://github.com/PolymerElements/paper-button/blob/2.x/paper-button.html */
      /* BEGIN: Copy from paper-button */
      paper-button[elevation="1"] {
        @apply --paper-material-elevation-1;
      }
      paper-button[elevation="2"] {
        @apply --paper-material-elevation-2;
      }
      paper-button[elevation="3"] {
        @apply --paper-material-elevation-3;
      }
      paper-button[elevation="4"] {
        @apply --paper-material-elevation-4;
      }
      paper-button[elevation="5"] {
        @apply --paper-material-elevation-5;
      }
      /* END: Copy from paper-button */
      paper-button:hover {
        background: linear-gradient(
            rgba(0, 0, 0, .12),
            rgba(0, 0, 0, .12)
        ), var(--background-color);
      }

      /* Some mobile browsers treat focused element as hovered element.
      As a result, element remains hovered after click (has grey background in default theme).
      Use @media (hover:none) to remove background if
      user's primary input mechanism can't hover over elements.
      See: https://developer.mozilla.org/en-US/docs/Web/CSS/@media/hover

      Note 1: not all browsers support this media query
      (see https://caniuse.com/#feat=css-media-interaction).
      If browser doesn't support it, then the whole content of @media .. is ignored.
      This is why the default behavior is placed outside of @media.
      */
      @media (hover:none) {
        paper-button:hover {
          background: transparent;
        }
      }

      :host([primary]) {
        --background-color: var(--primary-button-background-color);
        --text-color: var(--primary-button-text-color);
      }
      :host([link][primary]) {
        --text-color: var(--primary-button-background-color);
      }

      /* Keep below color definition for primary so that this takes precedence
        when disabled. */
      :host([disabled]), :host([loading]) {
        --background-color: var(--disabled-button-background-color);
        --text-color: var(--deemphasized-text-color);
        cursor: default;
      }

      /* Styles for link buttons specifically */
      :host([link]) {
        --background-color: transparent;
        --margin: 0;
        --padding: 5px 4px;
      }
      :host([disabled][link]),
      :host([loading][link]) {
        --background-color: transparent;
        --text-color: var(--deemphasized-text-color);
        cursor: default;
      }

      /* Styles for the optional down arrow */
      :host(:not([down-arrow])) .downArrow {
        display: none;
      }
      :host([down-arrow]) .downArrow {
        border-top: .36em solid #ccc;
        border-left: .36em solid transparent;
        border-right: .36em solid transparent;
        margin-bottom: var(--spacing-xxs);
        margin-left: var(--spacing-m);
        transition: border-top-color 200ms;
      }
      :host([down-arrow]) paper-button:hover .downArrow {
        border-top-color: var(--deemphasized-text-color);
      }
    </style>
    <paper-button raised="[[!link]]" disabled="[[_disabled]]" tabindex="-1">
      <template is="dom-if" if="[[loading]]">
        <span class="loadingSpin"></span>
      </template>
      <slot></slot>
      <i class="downArrow"></i>
    </paper-button>
`;
