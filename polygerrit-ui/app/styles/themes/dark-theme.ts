/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {safeStyleSheet, setStyleTextContent} from '../../utils/inner-html-util';

// TODO: Replace `html` with `html.darkTheme`. But before we can do that we have
// to ensure that all plugins also use `.darkTheme`, otherwise we would trump
// their sepcificity here. When we do that we can also always execute
// applyTheme() below (similar to app-theme).
const darkThemeCss = safeStyleSheet`
  html {
    --gerrit-blue-light: #1565c0;
    --gerrit-blue-dark: #90caf9;
    --red-50: #fce8e6;
    --red-100: #fad2cf;
    --red-200: #f6aea9;
    --red-300: #f28b82;
    --red-400: #ee675c;
    --red-500: #ea4335;
    --red-600: #d93025;
    --red-700: #c5221f;
    --red-700-04: #c5221f0a;
    --red-700-10: #c5221f1a;
    --red-700-12: #c5221f1f;
    --red-800: #b31412;
    --red-900: #a50e0e;
    --red-tonal: #6c322f;
    --blue-50: #e8f0fe;
    --blue-100: #d2e3fc;
    --blue-200: #aecbfa;
    --blue-200-16: #aecbfa29;
    --blue-200-24: #aecbfa3d;
    --blue-300: #8ab4f8;
    --blue-300-24: #8ab4f83D;
    --blue-400: #669df6;
    --blue-500: #4285f4;
    --blue-600: #1a73e8;
    --blue-700: #1967d2;
    --blue-700-04: #1967d20a;
    --blue-700-10: #1967d21a;
    --blue-700-12: #1967d21f;
    --blue-700-16: #1967d229;
    --blue-700-24: #1967d23d;
    --blue-800: #185abc;
    --blue-900: #174ea6;
    --blue-tonal: #314972;
    --orange-50: #feefe3;
    --orange-100: #fedfc8;
    --orange-200: #fdc69c;
    --orange-300: #fcad70;
    --orange-400: #fa903e;
    --orange-500: #fa7b17;
    --orange-600: #e8710a;
    --orange-700: #d56e0c;
    --orange-700-04: #d56e0c0a;
    --orange-700-10: #d56e0c1a;
    --orange-700-12: #d56e0c1f;
    --orange-800: #c26401;
    --orange-900: #b06000;
    --orange-tonal: #714625;
    --cyan-50: #e4f7fb;
    --cyan-100: #cbf0f8;
    --cyan-200: #a1e4f2;
    --cyan-300: #78d9ec;
    --cyan-400: #4ecde6;
    --cyan-500: #24c1e0;
    --cyan-600: #12b5cb;
    --cyan-700: #129eaf;
    --cyan-800: #098591;
    --cyan-900: #007b83;
    --cyan-tonal: #275e6b;
    --green-50: #e6f4ea;
    --green-100: #ceead6;
    --green-200: #a8dab5;
    --green-300: #81c995;
    --green-400: #5bb974;
    --green-500: #34a853;
    --green-600: #1e8e3e;
    --green-700: #188038;
    --green-700-04: #1880380a;
    --green-700-10: #1880381a;
    --green-700-12: #1880381f;
    --green-800: #137333;
    --green-900: #0d652d;
    --green-tonal: #2c553a;
    --gray-900: #202124;
    --gray-800: #3c4043;
    --gray-800-12: #3c40431f;
    --gray-800-38: #3c404361;
    --gray-700: #5f6368;
    --gray-700-04: #5f63680a;
    --gray-700-10: #5f63681a;
    --gray-700-12: #5f63681f;
    --gray-500: #9aa0a6;
    --gray-400: #bdc1c6;
    --gray-300: #dadce0;
    --gray-200: #e8eaed;
    --gray-200-12: #e8eaed1f;
    --gray-200-38: #e8eaed61;
    --gray-100: #f1f3f4;
    --gray-50: #f8f9fa;
    --gray-tonal: #505357;
    --purple-50: #f3e8fd;
    --purple-100: #e9d2fd;
    --purple-200: #d7aefb;
    --purple-300: #c58af9;
    --purple-400: #af5cf7;
    --purple-500: #a142f4;
    --purple-600: #9334e6;
    --purple-700: #8430ce;
    --purple-800: #7627bb;
    --purple-900: #681da8;
    --purple-tonal: #523272;
    --deep-purple-800: #4527a0;
    --deep-purple-600: #5e35b1;
    --pink-50: #fde7f3;
    --pink-100: #fdcfe8;
    --pink-200: #fba9d6;
    --pink-300: #ff8bcb;
    --pink-400: #ff63b8;
    --pink-500: #f439a0;
    --pink-600: #e52592;
    --pink-700: #c92786;
    --pink-800: #b80672;
    --pink-900: #9c166b;
    --pink-tonal: #702f55;
    --yellow-50: #fef7e0;
    --yellow-100: #feefc3;
    --yellow-200: #fde293;
    --yellow-300: #fdd663;
    --yellow-400: #fcc934;
    --yellow-500: #fbbc04;
    --yellow-600: #f9ab00;
    --yellow-700: #f29900;
    --yellow-800: #ea8600;
    --yellow-900: #e37400;
    --yellow-tonal: #6a5619;
    --brown-50: #efebe9;
    --brown-tonal: #6d4c41;
    --white-04: #ffffff0a;
    --white-10: #ffffff1a;
    --white-12: #ffffff1f;

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
    --indirect-relation-text-color: var(--green-200);

    /* background colors */
    /* primary background colors */
    --background-color-primary: var(--gray-900);
    --background-color-secondary: #2f3034;
    --background-color-tertiary: var(--gray-800);
    /* directly derived from primary background colors */
    /*   empty, because inheriting from app-theme is just fine
      /* unique background colors */
    --line-item-highlight-color: #3a361c;
    --line-item-highlight-selection-color: #423e24;
    --chip-selected-background-color: #3c4455;
    --edit-mode-background-color: #5c0a36;
    --emphasis-color: #383f4a;
    --hover-background-color: rgba(161, 194, 250, 0.2);
    --disabled-button-background-color: #484a4d;
    --selection-background-color: rgba(161, 194, 250, 0.1);
    --tooltip-background-color: var(--gray-200);
    --section-header-background-color: var(--blue-tonal);

    /* comment background colors */
    --comment-background-color: #3c3f43;
    --unresolved-comment-background-color: #614a19;

    /* Suggest edits */
    --user-suggestion-header-background: var(--gray-700);
    --user-suggestion-header-color: white;

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
    --status-merged: #a4a4a4;
    --status-abandoned: var(--gray-300);
    --status-wip: #bcaaa4;
    --status-private: var(--purple-200);
    --status-conflict: var(--red-300);
    --status-revert: var(--gray-200);
    --status-revert-created: #ff8a65;
    --status-active: #f4ce5d;
    --status-ready: #55c374;
    --status-custom: var(--purple-400);

    /* file status colors */
    --file-status-added: var(--green-400);
    --file-status-deleted: var(--red-300);
    --file-status-modified: var(--gray-500);
    --file-status-renamed: var(--orange-400);
    --file-status-unchanged: var(--gray-500);
    --file-status-reverted: var(--gray-500);

    /* fonts */

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
    --coverage-covered: var(--cyan-tonal);
    --coverage-covered-line-num-color: var(--gray-200);
    --coverage-not-covered: var(--orange-tonal);
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

    /* rules applied to html */
    background-color: var(--view-background-color);

    /* md-filled-card (colours originate from paper-card) */
    --card-surface-container-highest: #2f3034;

    /* md-checkbox (colours from paper-checkbox but adapted using material/web theme selector) */
    --checkbox-primary: #bac3ff;
    --checkbox-on-primary: #08218a;
    --checkbox-on-surface: #e3e1ea;
    --checkbox-on-surface-variant: #c5c5d4;

  /* These colours come from paper-checkbox */
    --radio-primary: #bac3ff;
    --radio-on-primary: #08218a;
    --radio-on-surface: #e3e1ea;
    --radio-on-surface-variant: #c5c5d4;

    /* md-filled-select/md-outlined-select (colour originates from paper-listbox but generated by material-web using the hex */
    --select-surface-container: #201f20;
    --select-surface-container-highest: #2f3034;
    --select-on-surface: #e5e2e1;
    --select-on-surface-variant: #c7c6cb;
    --select-primary: #c7c6cb;
    --select-secondary-container: #3d3d3f;
    --select-on-secondary-container: #d2d0d2;

    /* md-menu/md-menu-itme/md-focus-ring */
    --gr-dropdown-focus-ring-color: #c8c6c7;

    /* md-switch (colour originates from paper-toggle-button but generated by material-web using the hex */
    --switch-color-surface-container-highest: #31353b;
    --switch-color-on-surface: #e0e2ea;
    --switch-color-on-surface-variant: #c0c7d4;
    --switch-color-outline: #8a919e;
    --switch-color-primary: #a2c9ff;
    --switch-color-on-primary: #00315b;
    --switch-color-primary-container: #0077ce;
    --switch-color-on-primary-container: #ffffff;

    /* md-tabs */
    --tabs-color-on-surface: #e1e2e6;
  }
`;

export function applyTheme() {
  if (document.head.querySelector('#dark-theme')) return;
  const styleEl = document.createElement('style');
  styleEl.setAttribute('id', 'dark-theme');
  setStyleTextContent(styleEl, darkThemeCss);

  // We would like to insert the dark theme styles after the light theme such
  // that the dark theme values override the defaults in the light theme. But
  // OTOH we want to insert before any plugin provided styles, because we do NOT
  // want to override those.
  const pluginStyleEl = document.head.querySelector('style#plugin-style');
  document.head.insertBefore(styleEl, pluginStyleEl);
}

export function removeTheme() {
  const styleEl = document.head.querySelector('#dark-theme');
  styleEl?.remove();
}
