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

// Mark the file as a module. Otherwise typescript assumes this is a script
// and $_documentContainer is a global variable.
// See: https://www.typescriptlang.org/docs/handbook/modules.html
import {
  createStyle,
  safeStyleSheet,
  setInnerHtml,
} from '../../utils/inner-html-util';

const customStyle = document.createElement('custom-style');
customStyle.setAttribute('id', 'light-theme');

const styleSheet = safeStyleSheet`
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
    --red-900: #a50e0e;
    --red-700: #c5221f;
    --red-200: #f6aea9;
    --red-50: #fce8e6;
    --blue-900: #174ea6;
    --blue-700: #1967d2;
    --blue-200: #aecbfa;
    --blue-50: #e8f0fe;
    --orange-900: #b06000;
    --orange-700: #d56e0c;
    --orange-200: #fdc69c;
    --orange-50: #feefe3;
    --cyan-900: #007b83;
    --cyan-700: #129eaf;
    --cyan-100: #cbf0f8;
    --cyan-50: #e4f7fb;
    --green-900: #0d652d;
    --green-700: #188038;
    --green-200: #a8dab5;
    --green-50: #e6f4ea;
    --gray-900: #202124;
    --gray-700: #5f6368;
    --gray-300: #dadce0;
    --gray-100: #f1f3f4;
    --gray-50: #f8f9fa;

    --chip-color: var(--gray-900);
    --error-color: var(--red-900);
    --error-foreground: var(--red-700);
    --error-background: var(--red-50);
    --warning-foreground: var(--orange-700);
    --warning-background: var(--orange-50);
    --info-foreground: var(--blue-700);
    --info-background: var(--blue-50);
    --selected-foreground: var(--blue-700);
    --selected-background: var(--blue-50);
    --info-deemphasized-foreground: var(--gray-300);
    --info-deemphasized-background: var(--gray-50);
    --success-foreground: var(--green-700);
    --gray-foreground: var(--gray-700);
    --gray-background: var(--gray-100);
    --tag-background: var(--cyan-100);
    --label-background: var(--red-50);

    /* text colors */
    --primary-text-color: black;
    --link-color: var(--blue-700);
    --comment-text-color: black;
    --deemphasized-text-color: var(--gray-700);
    --default-button-text-color: var(--blue-700);
    --chip-selected-text-color: var(--default-button-text-color);
    --error-text-color: red;
    --primary-button-text-color: white;
      /* Used on text color for change list that doesn't need user's attention. */
    --reviewed-text-color: black;
    --vote-text-color: black;
    --status-text-color: white;
    --tooltip-text-color: white;
    --negative-red-text-color: #d93025;
    --positive-green-text-color: #188038;

    /* background colors */
    /* primary background colors */
    --background-color-primary: #ffffff;
    --background-color-secondary: #f8f9fa;
    --background-color-tertiary: #f1f3f4;
    /* directly derived from primary background colors */
    --chip-background-color: var(--background-color-tertiary);
    --default-button-background-color: var(--background-color-primary);
    --dialog-background-color: var(--background-color-primary);
    --dropdown-background-color: var(--background-color-primary);
    --expanded-background-color: var(--background-color-tertiary);
    --select-background-color: var(--background-color-secondary);
    --shell-command-background-color: var(--background-color-secondary);
    --shell-command-decoration-background-color: var(--background-color-tertiary);
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
    --disabled-button-background-color: #e8eaed;
    --primary-button-background-color: var(--blue-700);
    --selection-background-color: rgba(161, 194, 250, 0.1);
    --tooltip-background-color: #333;
    /* comment background colors */
    --comment-background-color: #e8eaed;
    --robot-comment-background-color: var(--blue-50);
    --unresolved-comment-background-color: #fef7e0;
    /* vote background colors */
    --vote-color-approved: #9fcc6b;
    --vote-color-disliked: #f7c4cb;
    --vote-color-neutral: #ebf5fb;
    --vote-color-recommended: #c9dfaf;
    --vote-color-rejected: #f7a1ad;

    /* misc colors */
    --border-color: var(--gray-300);
    --comment-separator-color: var(--gray-300);

    /* status colors */
    --status-merged: #188038;
    --status-abandoned: var(--gray-700);
    --status-wip: #795548;
    --status-private: #a142f4;
    --status-conflict: #d93025;
    --status-active: #1976d2;
    --status-ready: #b80672;
    --status-custom: #681da8;

    /* fonts */
    --font-family: 'Roboto', -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol';
    --header-font-family: 'Open Sans', 'Roboto', -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol';
    --monospace-font-family: 'Roboto Mono', 'SF Mono', 'Lucida Console', Monaco, monospace;
    --font-size-code: 12px;     /* 12px mono */
    --font-size-mono: .929rem;  /* 13px mono */
    --font-size-small: .857rem; /* 12px */
    --font-size-normal: 1rem;   /* 14px */
    --font-size-h3: 1.143rem;   /* 16px */
    --font-size-h2: 1.429rem;   /* 20px */
    --font-size-h1: 1.714rem;   /* 24px */
    --line-height-mono: 1.286rem;   /* 18px */
    --line-height-small: 1.143rem;  /* 16px */
    --line-height-normal: 1.429rem; /* 20px */
    --line-height-h3: 1.714rem;     /* 24px */
    --line-height-h2: 2rem;         /* 28px */
    --line-height-h1: 2.286rem;     /* 32px */
    --font-weight-normal: 400; /* 400 is the same as 'normal' */
    --font-weight-bold: 500;
    --font-weight-h1: 400;
    --font-weight-h2: 400;
    --font-weight-h3: 400;
    --context-control-button-font: var(--font-weight-normal) var(--font-size-normal) var(--font-family);

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
    --dark-rebased-add-highlight-color: #d7d7f9;
    --dark-rebased-remove-highlight-color: #f7e8b7;
    --dark-remove-highlight-color: #ffcdd2;
    --diff-blank-background-color: var(--background-color-secondary);
    --diff-context-control-background-color: #fff7d4;
    --diff-context-control-border-color: #f6e6a5;
    --diff-context-control-color: var(--default-button-text-color);
    --diff-highlight-range-color: rgba(255, 213, 0, 0.5);
    --diff-highlight-range-hover-color: rgba(255, 255, 0, 0.5);
    --diff-selection-background-color: #c7dbf9;
    --diff-tab-indicator-color: var(--deemphasized-text-color);
    --diff-trailing-whitespace-indicator: #ff9ad2;
    --light-add-highlight-color: #d8fed8;
    --light-rebased-add-highlight-color: #eef;
    --diff-moved-in-background: #e4f7fb;
    --diff-moved-out-background: #f3e8fd;
    --diff-moved-in-label-background: #007b83;
    --diff-moved-out-label-background: #681da8;
    --light-remove-add-highlight-color: #fff8dc;
    --light-remove-highlight-color: #ffebee;
    --coverage-covered: #e0f2f1;
    --coverage-not-covered: #ffd1a4;
    --ranged-comment-chip-background: #b06000;
    --ranged-comment-chip-text-color: #feefe3;

    /* syntax colors */
    --syntax-attr-color: #219;
    --syntax-attribute-color: var(--primary-text-color);
    --syntax-built_in-color: #30a;
    --syntax-comment-color: #3f7f5f;
    --syntax-default-color: var(--primary-text-color);
    --syntax-doctag-weight: bold;
    --syntax-function-color: var(--primary-text-color);
    --syntax-keyword-color: #9e0069;
    --syntax-link-color: #219;
    --syntax-literal-color: #219;
    --syntax-meta-color: #ff1717;
    --syntax-meta-keyword-color: #219;
    --syntax-number-color: #164;
    --syntax-params-color: var(--primary-text-color);
    --syntax-regexp-color: #fa8602;
    --syntax-selector-attr-color: #fa8602;
    --syntax-selector-class-color: #164;
    --syntax-selector-id-color: #2a00ff;
    --syntax-selector-pseudo-color: #fa8602;
    --syntax-string-color: #2a00ff;
    --syntax-tag-color: #170;
    --syntax-template-tag-color: #fa8602;
    --syntax-template-variable-color: #0000c0;
    --syntax-title-color: #0000c0;
    --syntax-type-color: var(--blue-700);
    --syntax-variable-color: var(--primary-text-color);

    /* elevation */
    --elevation-level-1: 0px 1px 2px 0px rgba(60, 64, 67, .30), 0px 1px 3px 1px rgba(60, 64, 67, .15);
    --elevation-level-2: 0px 1px 2px 0px rgba(60, 64, 67, .30), 0px 2px 6px 2px rgba(60, 64, 67, .15);
    --elevation-level-3: 0px 1px 3px 0px rgba(60, 64, 67, .30), 0px 4px 8px 3px rgba(60, 64, 67, .15);
    --elevation-level-4: 0px 2px 3px 0px rgba(60, 64, 67, .30), 0px 6px 10px 4px rgba(60, 64, 67, .15);
    --elevation-level-5: 0px 4px 4px 0px rgba(60, 64, 67, .30), 0px 8px 12px 6px rgba(60, 64, 67, .15);

    /* misc */
    --border-radius: 4px;
    --reply-overlay-z-index: 1000;

    /* paper and iron component overrides */
    --iron-overlay-backdrop-background-color: black;
    --iron-overlay-backdrop-opacity: 0.32;
    --iron-overlay-backdrop: {
      transition: none;
    };
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
  }`;

setInnerHtml(customStyle, createStyle(styleSheet));

document.head.appendChild(customStyle);
