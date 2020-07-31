/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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

function getStyleEl() {
  const $_documentContainer = document.createElement('template');
  $_documentContainer.innerHTML = `
  <custom-style id="dark-theme"><style is="custom-style">
    html {
      /**
       * Sections and variables must stay consistent with app-theme.js.
       *
       * Only modify color variables in this theme file. dark-theme extends
       * app-theme, so there is no need to repeat all variables, but for colors
       * it does make sense to list them all: If you override one color, then
       * you probably want to override all.
       */

      /* text colors */
      --primary-text-color: #e8eaed;
      --link-color: #8ab4f8;
      --comment-text-color: var(--primary-text-color);
      --deemphasized-text-color: #9aa0a6;
      --default-button-text-color: #8ab4f8;
      --chip-selected-text-color: #d2e3fc;
      --error-text-color: red;
      --primary-button-text-color: black;
        /* Used on text color for change list doesn't need user's attention. */
      --reviewed-text-color: #dadce0;
      --vote-text-color: black;
      --status-text-color: black;
      --tooltip-text-color: white;
      --negative-red-text-color: #f28b82;
      --positive-green-text-color: #81c995;

      /* background colors */
      /* primary background colors */
      --background-color-primary: #202124;
      --background-color-secondary: #2f3034;
      --background-color-tertiary: #3b3d3f;
      /* directly derived from primary background colors */
      /*   empty, because inheriting from app-theme is just fine
      /* unique background colors */
      --assignee-highlight-color: #3a361c;
      --chip-selected-background-color: #3c4455;
      --edit-mode-background-color: #5c0a36;
      --emphasis-color: #383f4a;
      --hover-background-color: rgba(161, 194, 250, 0.2);
      --disabled-button-background-color: #484a4d;
      --primary-button-background-color: var(--link-color);
      --selection-background-color: rgba(161, 194, 250, 0.1);
      --tooltip-background-color: #111;
      /* comment background colors */
      --comment-background-color: #3c3f43;
      --robot-comment-background-color: #1e3a5f;
      --unresolved-comment-background-color: #614a19;
      /* vote background colors */
      --vote-color-approved: #7fb66b;
      --vote-color-disliked: #bf6874;
      --vote-color-neutral: #597280;
      --vote-color-recommended: #3f6732;
      --vote-color-rejected: #ac2d3e;

      /* misc colors */
      --border-color: #5f6368;
      --comment-separator-color: var(--border-color);

      /* status colors */
      --status-merged: #5bb974;
      --status-abandoned: #dadce0;
      --status-wip: #bcaaa4;
      --status-private: #d7aefb;
      --status-conflict: #f28b82;
      --status-active: #669df6;
      --status-ready: #f439a0;
      --status-custom: #af5cf7;

      /* fonts */
      --font-weight-bold: 700; /* 700 is the same as 'bold' */

      /* spacing */

      /* header and footer */
      --footer-background-color: var(--background-color-tertiary);
      --footer-border-top: 1px solid var(--border-color);
      --header-background-color: var(--background-color-tertiary);
      --header-border-bottom: 1px solid var(--border-color);
      --header-padding: 0 var(--spacing-l);
      --header-text-color: var(--primary-text-color);

      /* diff colors */
      --dark-add-highlight-color: #133820;
      --dark-rebased-add-highlight-color: rgba(11, 255, 155, 0.15);
      --dark-rebased-remove-highlight-color: rgba(255, 139, 6, 0.15);
      --dark-remove-highlight-color: #62110f;
      --diff-blank-background-color: var(--background-color-secondary);
      --diff-context-control-background-color: #333311;
      --diff-context-control-border-color: var(--border-color);
      --diff-context-control-color: var(--deemphasized-text-color);
      --diff-highlight-range-color: rgba(0, 100, 200, 0.5);
      --diff-highlight-range-hover-color: rgba(0, 150, 255, 0.5);
      --diff-selection-background-color: #3a71d8;
      --diff-tab-indicator-color: var(--deemphasized-text-color);
      --diff-trailing-whitespace-indicator: #ff9ad2;
      --light-add-highlight-color: #0f401f;
      --light-rebased-add-highlight-color: #487165;
      --light-remove-add-highlight-color: #2f3f2f;
      --light-remove-highlight-color: #320404;
      --coverage-covered: #112826;
      --coverage-not-covered: #6b3600;

      /* syntax colors */
      --syntax-attr-color: #80cbbf;
      --syntax-attribute-color: var(--primary-text-color);
      --syntax-built_in-color: #f7c369;
      --syntax-comment-color: var(--deemphasized-text-color);
      --syntax-default-color: var(--primary-text-color);
      --syntax-doctag-weight: bold;
      --syntax-function-color: var(--primary-text-color);
      --syntax-keyword-color: #cd4cf0;
      --syntax-link-color: #c792ea;
      --syntax-literal-color: #eefff7;
      --syntax-meta-color: #6d7eee;
      --syntax-meta-keyword-color: #eefff7;
      --syntax-number-color: #00998a;
      --syntax-params-color: var(--primary-text-color);
      --syntax-regexp-color: #f77669;
      --syntax-selector-attr-color: #80cbbf;
      --syntax-selector-class-color: #ffcb68;
      --syntax-selector-id-color: #f77669;
      --syntax-selector-pseudo-color: #c792ea;
      --syntax-string-color: #c3e88d;
      --syntax-tag-color: #f77669;
      --syntax-template-tag-color: #c792ea;
      --syntax-template-variable-color: #f77669;
      --syntax-title-color: #75a5ff;
      --syntax-type-color: #dd5f5f;
      --syntax-variable-color: #f77669;

      /* misc */

      /* paper and iron component overrides */
      --iron-overlay-backdrop-background-color: white;

      /* rules applied to <html> */
      background-color: var(--view-background-color);
    }
  </style></custom-style>`;

  return $_documentContainer;
}

export function applyTheme() {
  document.head.appendChild(getStyleEl().content);
}

export function removeTheme() {
  const darkThemeEls = document.head.querySelectorAll('#dark-theme');
  if (darkThemeEls.length) {
    darkThemeEls.forEach(darkThemeEl => darkThemeEl.remove());
  }
}
