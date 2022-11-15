/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {safeStyleSheet, safeStyleEl} from '../../utils/inner-html-util';

// TODO: Replace `html` with `html.darkTheme`. But before we can do that we have
// to ensure that all plugins also use `.darkTheme`, otherwise we would trump
// their sepcificity here. When we do that we can also always execute
// applyTheme() below (similar to app-theme).
const darkThemeCss = safeStyleSheet`
  html {
    /**
       * Sections and variables must stay consistent with app-theme.js.
       *
       * Only modify color variables in this theme file. dark-theme extends
       * app-theme, so there is no need to repeat all variables, but for colors
       * it does make sense to list them all: If you override one color, then
       * you probably want to override all.
       */

    --error-foreground: var(--red-200);
    --error-background: var(--red-tonal);
    --error-background-hover: linear-gradient(var(--white-04), var(--white-04)),
      var(--red-tonal);
    --error-background-focus: linear-gradient(var(--white-12), var(--white-12)),
      var(--red-tonal);
    --error-ripple: var(--white-10);

    --code-review-warning-background: var(--blue-tonal);

    --warning-foreground: var(--orange-200);
    --warning-background: var(--orange-tonal);
    --warning-background-hover: linear-gradient(
        var(--white-04),
        var(--white-04)
      ),
      var(--orange-tonal);
    --warning-background-focus: linear-gradient(
        var(--white-12),
        var(--white-12)
      ),
      var(--orange-tonal);
    --warning-ripple: var(--white-10);

    --info-foreground: var(--blue-200);
    --info-background: var(--blue-tonal);
    --info-background-hover: linear-gradient(var(--white-04), var(--white-04)),
      var(--blue-tonal);
    --info-background-focus: linear-gradient(var(--white-12), var(--white-12)),
      var(--blue-tonal);
    --info-ripple: var(--white-10);

    --primary-button-text-color: black;
    --primary-button-background-color: var(--gerrit-blue-dark);
    --primary-button-background-hover: var(--blue-200-16);
    --primary-button-background-focus: var(--blue-200-24);

    --selected-foreground: var(--blue-200);
    --selected-background: var(--blue-900);
    --selected-chip-background: var(--blue-300-24);

    --success-foreground: var(--green-200);
    --success-background: var(--green-tonal);
    --success-background-hover: linear-gradient(
        var(--white-04),
        var(--white-04)
      ),
      var(--green-tonal);
    --success-background-focus: linear-gradient(
        var(--white-12),
        var(--white-12)
      ),
      var(--green-tonal);
    --success-ripple: var(--white-10);

    --gray-foreground: var(--gray-300);
    --gray-background: var(--gray-tonal);
    --gray-background-hover: linear-gradient(var(--white-04), var(--white-04)),
      var(--gray-tonal);
    --gray-background-focus: linear-gradient(var(--white-12), var(--white-12)),
      var(--gray-tonal);
    --gray-ripple: var(--white-10);

    --disabled-foreground: var(--gray-200-38);
    --disabled-background: var(--gray-200-12);

    --chip-color: var(--gray-100);
    --error-color: var(--red-200);
    --tag-background: var(--cyan-900);
    --label-background: var(--red-900);

    --not-working-hours-icon-background-color: var(--purple-tonal);
    --not-working-hours-icon-color: var(--purple-100);
    --unavailability-icon-color: var(--gray-500);
    --unavailability-chip-icon-color: var(--orange-700);
    --unavailability-chip-background-color: var(--orange-tonal);

    /* text colors */
    --primary-text-color: var(--gray-200);
    --link-color: var(--gerrit-blue-dark);
    --comment-text-color: var(--primary-text-color);
    --deemphasized-text-color: var(--gray-400);
    --default-button-text-color: var(--gerrit-blue-dark);
    --chip-selected-text-color: var(--blue-100);
    --error-text-color: var(--red-200);
    /* Used on text color for change list doesn't need user's attention. */
    --reviewed-text-color: var(--gray-300);
    --vote-text-color: black;
    --status-text-color: black;
    --tooltip-text-color: var(--gray-900);
    --tooltip-button-text-color: var(--gerrit-blue-light);
    --negative-red-text-color: var(--red-200);
    --positive-green-text-color: var(--green-200);
    --indirect-ancestor-text-color: var(--green-200);

    /* background colors */
    /* primary background colors */
    --background-color-primary: var(--gray-900);
    --background-color-secondary: #2f3034;
    --background-color-tertiary: var(--gray-800);
    /* directly derived from primary background colors */
    /*   empty, because inheriting from app-theme is just fine
      /* unique background colors */
    --assignee-highlight-color: #3a361c;
    --assignee-highlight-selection-color: #423e24;
    --chip-selected-background-color: #3c4455;
    --edit-mode-background-color: #5c0a36;
    --emphasis-color: #383f4a;
    --hover-background-color: rgba(161, 194, 250, 0.2);
    --disabled-button-background-color: #484a4d;
    --selection-background-color: rgba(161, 194, 250, 0.1);
    --tooltip-background-color: var(--gray-200);

    /* comment background colors */
    --comment-background-color: #3c3f43;
    --robot-comment-background-color: #1e3a5f;
    --unresolved-comment-background-color: #614a19;

    /* vote background colors */
    --vote-color-approved: var(--green-300);
    --vote-color-disliked: var(--red-tonal);
    --vote-outline-disliked: var(--red-200);
    --vote-color-neutral: var(--gray-700);
    --vote-color-recommended: var(--green-tonal);
    --vote-outline-recommended: var(--green-200);
    --vote-color-rejected: var(--red-200);

    /* vote chip background colors */
    --vote-chip-unselected-outline-color: var(--gray-500);
    --vote-chip-unselected-color: var(--grey-800);
    --vote-chip-selected-positive-color: var(--green-200);
    --vote-chip-selected-neutral-color: var(--gray-300);
    --vote-chip-selected-negative-color: var(--red-200);
    --vote-chip-unselected-text-color: white;
    --vote-chip-selected-text-color: black;

    --outline-color-focus: var(--gray-100);

    /* misc colors */
    --border-color: var(--gray-700);
    --input-focus-border-color: var(--blue-200);
    --comment-separator-color: var(--border-color);

    /* checks tag colors */
    --tag-gray: var(--gray-tonal);
    --tag-yellow: var(--yellow-tonal);
    --tag-pink: var(--pink-tonal);
    --tag-purple: var(--purple-tonal);
    --tag-cyan: var(--cyan-tonal);
    --tag-brown: var(--brown-tonal);

    /* status colors */
    --status-merged: var(--green-400);
    --status-abandoned: var(--gray-300);
    --status-wip: #bcaaa4;
    --status-private: var(--purple-200);
    --status-conflict: var(--red-300);
    --status-revert-created: #ff8a65;
    --status-active: var(--blue-400);
    --status-ready: var(--pink-500);
    --status-custom: var(--purple-400);

    /* file status colors */
    --file-status-added: var(--green-400);
    --file-status-deleted: var(--red-300);
    --file-status-modified: var(--gray-500);
    --file-status-renamed: var(--orange-400);
    --file-status-unchanged: var(--gray-500);
    --file-status-reverted: var(--gray-500);

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

    /* dashboard size background colors */
    --dashboard-size-xs: var(--gray-700);
    --dashboard-size-s: var(--gray-500);
    --dashboard-size-m: var(--gray-400);
    --dashboard-size-l: var(--gray-300);
    --dashboard-size-xl: var(--gray-200);
    --dashboard-size-text: black;
    --dashboard-size-xs-text: white;
    --dashboard-size-xl-text: black;

    /* diff colors */
    --dark-add-highlight-color: var(--green-tonal);
    --light-add-highlight-color: #182b1f;
    --dark-remove-highlight-color: #62110f;
    --light-remove-highlight-color: #320404;

    --dark-rebased-add-highlight-color: var(--deep-purple-800);
    --light-rebased-add-highlight-color: var(--deep-purple-600);
    --dark-rebased-remove-highlight-color: rgba(255, 139, 6, 0.15);
    --light-rebased-remove-highlight-color: #2f3f2f;

    --diff-moved-in-background: #1d4042;
    --diff-moved-in-label-color: var(--cyan-50);
    --diff-moved-in-changed-background: #1d4042;
    --diff-moved-in-changed-label-color: var(--cyan-50);
    --diff-moved-out-background: #230e34;
    --diff-moved-out-label-color: var(--purple-50);

    --diff-blank-background-color: var(--background-color-secondary);
    --diff-context-control-background-color: #333311;
    --diff-context-control-border-color: var(--border-color);
    --diff-context-control-color: var(--deemphasized-text-color);
    --diff-highlight-range-color: rgba(0, 100, 200, 0.5);
    --diff-highlight-range-hover-color: rgba(0, 150, 255, 0.5);
    --diff-selection-background-color: #3a71d8;
    --diff-tab-indicator-color: var(--deemphasized-text-color);
    --diff-trailing-whitespace-indicator: #ff9ad2;
    --focused-line-outline-color: var(--blue-200);
    --coverage-covered: #37674a;
    --coverage-covered-line-num-color: var(--gray-200);
    --coverage-not-covered: #6b3600;
    --ranged-comment-hint-text-color: var(--blue-50);
    --token-highlighting-color: var(--yellow-tonal);

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
    --syntax-property-color: #c792ea;
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
    --syntax-title-function-color: var(--syntax-title-color);
    --syntax-type-color: #dd5f5f;
    --syntax-variable-color: #f77669;
    --syntax-variable-language-color: var(--syntax-built_in-color);

    /* misc */
    --line-length-indicator-color: #d7aefb;

    /* paper and iron component overrides */
    --iron-overlay-backdrop-background-color: white;

    /* rules applied to html */
    background-color: var(--view-background-color);
  }
`;

export function applyTheme() {
  if (document.head.querySelector('#dark-theme')) return;
  const styleEl = document.createElement('style');
  styleEl.setAttribute('id', 'dark-theme');
  safeStyleEl.setTextContent(styleEl, darkThemeCss);
  document.head.appendChild(styleEl);
}

export function removeTheme() {
  const styleEl = document.head.querySelector('#dark-theme');
  styleEl?.remove();
}
