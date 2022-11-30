/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {safeStyleSheet, safeStyleEl} from '../../utils/inner-html-util';

const appThemeCss = safeStyleSheet`
  html {
    /**
       * When adding a new color variable make sure to also add it to the other
       * theme files in the same directory.
       *
       * For colors prefer lower case hex colors.
       *
       * Note that plugins might be using these variables, so removing a variable
       * can be a breaking change that should go into the release notes.
       */

    /* color palette */
    --gerrit-blue-light: #1565c0;
    --gerrit-blue-dark: #90caf9;
    --red-900: #a50e0e;
    --red-700: #c5221f;
    --red-700-04: #c5221f0a;
    --red-700-10: #c5221f1a;
    --red-700-12: #c5221f1f;
    --red-600: #d93025;
    --red-300: #f28b82;
    --red-200: #f6aea9;
    --red-50: #fce8e6;
    --red-tonal: #6c322f;
    --blue-900: #174ea6;
    --blue-800: #185abc;
    --blue-700: #1967d2;
    --blue-700-04: #1967d20a;
    --blue-700-10: #1967d21a;
    --blue-700-12: #1967d21f;
    --blue-700-16: #1967d229;
    --blue-700-24: #1967d23d;
    --blue-400: #669df6;
    --blue-300: #8ab4f8;
    --blue-300-24: #8ab4f83D;
    --blue-200: #aecbfa;
    --blue-200-16: #aecbfa29;
    --blue-200-24: #aecbfa3d;
    --blue-100: #d2e3fc;
    --blue-50: #e8f0fe;
    --blue-tonal: #314972;
    --orange-900: #b06000;
    --orange-800: #c26401;
    --orange-700: #d56e0c;
    --orange-700-04: #d56e0c0a;
    --orange-700-10: #d56e0c1a;
    --orange-700-12: #d56e0c1f;
    --orange-400: #fa903e;
    --orange-300: #fcad70;
    --orange-200: #fdc69c;
    --orange-50: #feefe3;
    --orange-tonal: #714625;
    --cyan-900: #007b83;
    --cyan-700: #129eaf;
    --cyan-200: #a1e4f2;
    --cyan-100: #cbf0f8;
    --cyan-50: #e4f7fb;
    --cyan-tonal: #275e6b;
    --green-900: #0d652d;
    --green-700: #188038;
    --green-700-04: #1880380a;
    --green-700-10: #1880381a;
    --green-700-12: #1880381f;
    --green-400: #5bb974;
    --green-300: #81c995;
    --green-200: #a8dab5;
    --green-50: #e6f4ea;
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
    --purple-900: #681da8;
    --purple-700: #8430ce;
    --purple-500: #a142f4;
    --purple-400: #af5cf7;
    --purple-200: #d7aefb;
    --purple-100: #e9d2fd;
    --purple-50: #f3e8fd;
    --purple-tonal: #523272;
    --deep-purple-800: #4527a0;
    --deep-purple-600: #5e35b1;
    --pink-800: #b80672;
    --pink-500: #f538a0;
    --pink-50: #fde7f3;
    --pink-tonal: #702f55;
    --yellow-50: #fef7e0;
    --yellow-tonal: #6a5619;
    --brown-50: #efebe9;
    --brown-tonal: #6d4c41;
    --white-04: #ffffff0a;
    --white-10: #ffffff1a;
    --white-12: #ffffff1f;

    --modal-opacity: 0.32;

    --error-foreground: var(--red-700);
    --error-background: var(--red-50);
    --error-background-hover: linear-gradient(
        var(--red-700-04),
        var(--red-700-04)
      ),
      var(--red-50);
    --error-background-focus: linear-gradient(
        var(--red-700-12),
        var(--red-700-12)
      ),
      var(--red-50);
    --error-ripple: var(--red-700-10);

    --code-review-warning-background: var(--blue-50);

    --warning-foreground: var(--orange-700);
    --warning-background: var(--orange-50);
    --warning-background-hover: linear-gradient(
        var(--orange-700-04),
        var(--orange-700-04)
      ),
      var(--orange-50);
    --warning-background-focus: linear-gradient(
        var(--orange-700-12),
        var(--orange-700-12)
      ),
      var(--orange-50);
    --warning-ripple: var(--orange-700-10);

    --info-foreground: var(--blue-700);
    --info-background: var(--blue-50);
    --info-background-hover: linear-gradient(
        var(--blue-700-04),
        var(--blue-700-04)
      ),
      var(--blue-50);
    --info-background-focus: linear-gradient(
        var(--blue-700-12),
        var(--blue-700-12)
      ),
      var(--blue-50);
    --info-ripple: var(--blue-700-10);

    --primary-button-text-color: white;
    --primary-button-background-color: var(--gerrit-blue-light);
    --primary-button-background-hover: var(--blue-700-16);
    --primary-button-background-focus: var(--blue-700-24);

    --selected-foreground: var(--blue-800);
    --selected-background: var(--blue-50);
    --selected-chip-background: var(--blue-50);

    --success-foreground: var(--green-700);
    --success-background: var(--green-50);
    --success-background-hover: linear-gradient(
        var(--green-700-04),
        var(--green-700-04)
      ),
      var(--green-50);
    --success-background-focus: linear-gradient(
        var(--green-700-12),
        var(--green-700-12)
      ),
      var(--green-50);
    --success-ripple: var(--green-700-10);

    --gray-foreground: var(--gray-700);
    --gray-background: var(--gray-100);
    --gray-background-hover: linear-gradient(
        var(--gray-700-04),
        var(--gray-700-04)
      ),
      var(--gray-100);
    --gray-background-focus: linear-gradient(
        var(--gray-700-12),
        var(--gray-700-12)
      ),
      var(--gray-100);
    --gray-ripple: var(--gray-700-10);

    --disabled-foreground: var(--gray-800-38);
    --disabled-background: var(--gray-800-12);

    --chip-color: var(--gray-900);
    --error-color: var(--red-900);
    --tag-background: var(--cyan-100);
    --label-background: var(--red-50);

    --not-working-hours-icon-background-color: var(--purple-50);
    --not-working-hours-icon-color: var(--purple-700);
    --unavailability-icon-color: var(--gray-700);
    --unavailability-chip-icon-color: var(--orange-900);
    --unavailability-chip-background-color: var(--yellow-50);

    /* text colors */
    --primary-text-color: var(--gray-900);
    --link-color: var(--gerrit-blue-light);
    --comment-text-color: var(--gray-900);
    --deemphasized-text-color: var(--gray-700);
    --default-button-text-color: var(--gerrit-blue-light);
    --chip-selected-text-color: var(--selected-foreground);
    --error-text-color: var(--red-700);
    /* Used on text color for change list that doesn't need user's attention. */
    --reviewed-text-color: black;
    --vote-text-color: black;
    --status-text-color: white;
    --tooltip-text-color: white;
    --tooltip-button-text-color: var(--gerrit-blue-dark);
    --negative-red-text-color: var(--red-600);
    --positive-green-text-color: var(--green-700);
    --indirect-descendant-text-color: var(--green-700);

    /* background colors */
    /* primary background colors */
    --background-color-primary: white;
    --background-color-secondary: var(--gray-50);
    --background-color-tertiary: var(--gray-100);
    /* directly derived from primary background colors */
    --chip-background-color: var(--background-color-tertiary);
    --default-button-background-color: var(--background-color-primary);
    --dialog-background-color: var(--background-color-primary);
    --dropdown-background-color: var(--background-color-primary);
    --expanded-background-color: var(--background-color-tertiary);
    --select-background-color: var(--background-color-secondary);
    --shell-command-background-color: var(--background-color-secondary);
    --shell-command-decoration-background-color: var(
      --background-color-tertiary
    );
    --table-header-background-color: var(--background-color-secondary);
    --table-subheader-background-color: var(--background-color-tertiary);
    --view-background-color: var(--background-color-primary);
    /* unique background colors */
    --assignee-highlight-color: #fcfad6;
    /* TODO: Find a nicer way to combine the --assignee-highlight-color and the
       --selection-background-color than to just invent another unique color. */
    --assignee-highlight-selection-color: #f6f4d0;
    --chip-selected-background-color: var(--blue-50);
    --edit-mode-background-color: #ebf5fb;
    --emphasis-color: #fff9c4;
    --hover-background-color: rgba(161, 194, 250, 0.2);
    --disabled-button-background-color: var(--disabled-background);
    --selection-background-color: rgba(161, 194, 250, 0.1);
    --tooltip-background-color: var(--gray-900);

    /* dashboard size background colors */
    --dashboard-size-xs: var(--gray-200);
    --dashboard-size-s: var(--gray-300);
    --dashboard-size-m: var(--gray-400);
    --dashboard-size-l: var(--gray-500);
    --dashboard-size-xl: var(--gray-700);
    --dashboard-size-text: black;
    --dashboard-size-xs-text: black;
    --dashboard-size-xl-text: white;

    /* comment background colors */
    --comment-background-color: var(--gray-200);
    --robot-comment-background-color: var(--blue-50);
    --unresolved-comment-background-color: #fef7e0;

    /* vote background colors */
    --vote-color-approved: var(--green-300);
    --vote-color-disliked: var(--red-50);
    --vote-outline-disliked: var(--red-700);
    --vote-color-neutral: var(--gray-300);
    --vote-color-recommended: var(--green-50);
    --vote-outline-recommended: var(--green-700);
    --vote-color-rejected: var(--red-300);

    /* vote chip background colors */
    --vote-chip-unselected-outline-color: var(--gray-500);
    --vote-chip-unselected-color: white;
    --vote-chip-selected-positive-color: var(--green-300);
    --vote-chip-selected-neutral-color: var(--gray-300);
    --vote-chip-selected-negative-color: var(--red-300);
    --vote-chip-unselected-text-color: black;
    --vote-chip-selected-text-color: black;

    --outline-color-focus: var(--gray-900);

    /* misc colors */
    --border-color: var(--gray-300);
    --input-focus-border-color: var(--blue-800);
    --comment-separator-color: var(--gray-300);
    --comment-quote-marker-color: var(--gray-500);

    /* checks tag colors */
    --tag-gray: var(--gray-200);
    --tag-yellow: var(--yellow-50);
    --tag-pink: var(--pink-50);
    --tag-purple: var(--purple-50);
    --tag-cyan: var(--cyan-50);
    --tag-brown: var(--brown-50);

    /* status colors */
    --status-merged: var(--green-700);
    --status-abandoned: var(--gray-700);
    --status-wip: #795548;
    --status-private: var(--purple-500);
    --status-conflict: var(--red-600);
    --status-revert-created: #e64a19;
    --status-active: var(--blue-700);
    --status-ready: var(--pink-800);
    --status-custom: var(--purple-900);

    /* file status colors */
    --file-status-font-color: black;
    --file-status-added: var(--green-300);
    --file-status-deleted: var(--red-200);
    --file-status-modified: var(--gray-300);
    --file-status-renamed: var(--orange-300);
    --file-status-unchanged: var(--gray-300);
    --file-status-reverted: var(--gray-300);

    /* fonts */
    --font-family: 'Roboto', -apple-system, BlinkMacSystemFont, 'Segoe UI',
      Helvetica, Arial, sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji',
      'Segoe UI Symbol';
    --header-font-family: 'Open Sans', 'Roboto', -apple-system,
      BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif,
      'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol';
    --monospace-font-family: 'Roboto Mono', 'SF Mono', 'Lucida Console', Monaco,
      monospace;
    --font-size-code: 12px; /* 12px mono */
    --font-size-mono: 0.929rem; /* 13px mono */
    --font-size-small: 0.857rem; /* 12px */
    --font-size-normal: 1rem; /* 14px */
    --font-size-h3: 1.143rem; /* 16px */
    --font-size-h2: 1.429rem; /* 20px */
    --font-size-h1: 1.714rem; /* 24px */
    --line-height-mono: 1.286rem; /* 18px */
    --line-height-small: 1.143rem; /* 16px */
    --line-height-normal: 1.429rem; /* 20px */
    --line-height-h3: 1.715rem; /* 24px */
    --line-height-h2: 2rem; /* 28px */
    --line-height-h1: 2.286rem; /* 32px */
    --font-weight-normal: 400; /* 400 is the same as 'normal' */
    --font-weight-bold: 500;
    --font-weight-h1: 400;
    --font-weight-h2: 400;
    --font-weight-h3: 400;
    --font-weight-h4: 600;
    --context-control-button-font: var(--font-weight-normal)
      var(--font-size-normal) var(--font-family);
    --code-hint-font-weight: 500;
    --image-diff-button-font: var(--font-weight-normal) var(--font-size-normal)
      var(--font-family);

    /* spacing */
    --spacing-xxs: 1px;
    --spacing-xs: 2px;
    --spacing-s: 4px;
    --spacing-m: 8px;
    --spacing-l: 12px;
    --spacing-xl: 16px;
    --spacing-xxl: 24px;

    /* header and footer */
    --footer-background-color: transparent;
    --footer-border-top: none;
    --header-background-color: var(--background-color-tertiary);
    --header-border-bottom: 1px solid var(--border-color);
    --header-border-image: '';
    --header-box-shadow: none;
    --header-padding: 0 var(--spacing-l);
    --header-icon-size: 0em;
    --header-icon: none;
    --header-text-color: black;
    --header-title-content: 'Gerrit';
    --header-title-font-size: 1.75rem;

    /* diff colors */
    --dark-add-highlight-color: #aaf2aa;
    --light-add-highlight-color: #d8fed8;
    --dark-remove-highlight-color: #ffcdd2;
    --light-remove-highlight-color: #ffebee;

    --dark-rebased-add-highlight-color: #d7d7f9;
    --light-rebased-add-highlight-color: #eef;
    --dark-rebased-remove-highlight-color: #f7e8b7;
    --light-rebased-remove-highlight-color: #fff8dc;

    --diff-moved-in-background: var(--cyan-50);
    --diff-moved-in-label-color: var(--cyan-900);
    --diff-moved-in-changed-background: var(--cyan-50);
    --diff-moved-in-changed-label-color: var(--cyan-900);
    --diff-moved-out-background: var(--purple-50);
    --diff-moved-out-label-color: var(--purple-900);

    --diff-blank-background-color: var(--background-color-secondary);
    --diff-context-control-background-color: #fff7d4;
    --diff-context-control-border-color: #f6e6a5;
    --diff-context-control-color: var(--default-button-text-color);
    --diff-highlight-range-color: rgba(255, 213, 0, 0.5);
    --diff-highlight-range-hover-color: rgba(255, 255, 0, 0.5);
    --diff-selection-background-color: #c7dbf9;
    --diff-tab-indicator-color: var(--deemphasized-text-color);
    --diff-trailing-whitespace-indicator: #ff9ad2;
    --focused-line-outline-color: var(--blue-700);
    --coverage-covered-line-num-color: var(--deemphasized-text-color);
    --coverage-covered: #e0f2f1;
    --coverage-not-covered: #ffd1a4;
    --ranged-comment-hint-text-color: var(--orange-900);
    --token-highlighting-color: #fffd54;

    /* syntax colors */
    --syntax-attr-color: #219;
    --syntax-attribute-color: var(--primary-text-color);
    --syntax-built_in-color: #30a;
    --syntax-bullet-color: var(--syntax-keyword-color);
    --syntax-code-color: var(--syntax-literal-color);
    --syntax-comment-color: #3f7f5f;
    --syntax-default-color: var(--primary-text-color);
    --syntax-doctag-weight: bold;
    --syntax-emphasis-color: var(--primary-text-color);
    --syntax-emphasis-style: italic;
    --syntax-emphasis-weight: normal;
    --syntax-formula-color: var(--syntax-regexp-color);
    --syntax-function-color: var(--primary-text-color);
    --syntax-keyword-color: #9e0069;
    --syntax-link-color: #219;
    --syntax-literal-color: #219;
    --syntax-meta-color: #ff1717;
    --syntax-meta-keyword-color: #219;
    --syntax-number-color: #164;
    --syntax-params-color: var(--primary-text-color);
    --syntax-property-color: var(--primary-text-color);
    --syntax-quote-color: var(--primary-text-color);
    --syntax-regexp-color: #fa8602;
    --syntax-section-color: var(--syntax-keyword-color);
    --syntax-section-style: normal;
    --syntax-section-weight: bold;
    --syntax-selector-attr-color: #fa8602;
    --syntax-selector-class-color: #164;
    --syntax-selector-id-color: #2a00ff;
    --syntax-selector-pseudo-color: #fa8602;
    --syntax-string-color: #2a00ff;
    --syntax-strong-color: var(--primary-text-color);
    --syntax-strong-style: normal;
    --syntax-strong-weight: bold;
    --syntax-tag-color: #170;
    --syntax-template-tag-color: #fa8602;
    --syntax-template-variable-color: #0000c0;
    --syntax-title-color: #0000c0;
    --syntax-title-function-color: var(--syntax-title-color);
    --syntax-type-color: var(--blue-700);
    --syntax-variable-color: var(--primary-text-color);
    --syntax-variable-language-color: var(--syntax-built_in-color);

    /* elevation */
    --elevation-level-1: 0px 1px 2px 0px rgba(60, 64, 67, 0.3),
      0px 1px 3px 1px rgba(60, 64, 67, 0.15);
    --elevation-level-2: 0px 1px 2px 0px rgba(60, 64, 67, 0.3),
      0px 2px 6px 2px rgba(60, 64, 67, 0.15);
    --elevation-level-3: 0px 1px 3px 0px rgba(60, 64, 67, 0.3),
      0px 4px 8px 3px rgba(60, 64, 67, 0.15);
    --elevation-level-4: 0px 2px 3px 0px rgba(60, 64, 67, 0.3),
      0px 6px 10px 4px rgba(60, 64, 67, 0.15);
    --elevation-level-5: 0px 4px 4px 0px rgba(60, 64, 67, 0.3),
      0px 8px 12px 6px rgba(60, 64, 67, 0.15);

    /* misc */
    --border-radius: 4px;
    --line-length-indicator-color: #681da8;

    /* paper and iron component overrides */
    --iron-overlay-backdrop-background-color: black;
    --iron-overlay-backdrop-opacity: 0.32;

    --paper-tooltip-delay-in: 200ms;
    --paper-tooltip-delay-out: 0;
    --paper-tooltip-duration-in: 0;
    --paper-tooltip-duration-out: 0;
    --paper-tooltip-background: var(--tooltip-background-color);
    --paper-tooltip-opacity: 1;
    --paper-tooltip-text-color: var(--tooltip-text-color);
  }
  @media screen and (max-width: 50em) {
    html {
      --spacing-xxs: 1px;
      --spacing-xs: 1px;
      --spacing-s: 2px;
      --spacing-m: 4px;
      --spacing-l: 8px;
      --spacing-xl: 12px;
      --spacing-xxl: 16px;
    }
  }
`;

const styleEl = document.createElement('style');
styleEl.setAttribute('id', 'light-theme');
safeStyleEl.setTextContent(styleEl, appThemeCss);
document.head.appendChild(styleEl);

// TODO: The following can be removed when Paper and Iron components have been
// removed from Gerrit.

const appThemeCssPolymerLegacy = safeStyleSheet`
  /* prettier formatter removes semi-colons after css mixins. */
  /* prettier-ignore */
  html {
    --paper-tooltip: {
      font-size: var(--font-size-small);
    };
    --iron-overlay-backdrop: {
      transition: none;
    };
  }
`;

const customStyleEl = document.createElement('custom-style');
const innerStyleEl = document.createElement('style');
safeStyleEl.setTextContent(innerStyleEl, appThemeCssPolymerLegacy);
customStyleEl.appendChild(innerStyleEl);
document.head.appendChild(customStyleEl);
