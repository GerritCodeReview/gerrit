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
export {};

const $_documentContainer = document.createElement('template');

$_documentContainer.innerHTML = `
<custom-style id="light-theme"><style is="custom-style">
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

    /* text colors */
    --primary-text-color: black;
    --link-color: #2a66d9;
    --comment-text-color: black;
    --deemphasized-text-color: #5F6368;
    --default-button-text-color: #2a66d9;
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
    --chip-selected-background-color: #e8f0fe;
    --edit-mode-background-color: #ebf5fb;
    --emphasis-color: #fff9c4;
    --hover-background-color: rgba(161, 194, 250, 0.2);
    --disabled-button-background-color: #e8eaed;
    --primary-button-background-color: #2a66d9;
    --selection-background-color: rgba(161, 194, 250, 0.1);
    --tooltip-background-color: #333;
    /* comment background colors */
    --comment-background-color: #e8eaed;
    --robot-comment-background-color: #e8f0fe;
    --unresolved-comment-background-color: #fef7e0;
    /* vote background colors */
    --vote-color-approved: #9fcc6b;
    --vote-color-disliked: #f7c4cb;
    --vote-color-neutral: #ebf5fb;
    --vote-color-recommended: #c9dfaf;
    --vote-color-rejected: #f7a1ad;

    /* misc colors */
    --border-color: #e8e8e8;
    --comment-separator-color: #dadce0;

    /* status colors */
    --status-merged: #188038;
    --status-abandoned: #5f6368;
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
    --line-height-code: 1.334;      /* 16px */
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
    --diff-context-control-color: var(--deemphasized-text-color);
    --diff-highlight-range-color: rgba(255, 213, 0, 0.5);
    --diff-highlight-range-hover-color: rgba(255, 255, 0, 0.5);
    --diff-selection-background-color: #c7dbf9;
    --diff-tab-indicator-color: var(--deemphasized-text-color);
    --diff-trailing-whitespace-indicator: #ff9ad2;
    --light-add-highlight-color: #d8fed8;
    --light-rebased-add-highlight-color: #eef;
    --light-remove-add-highlight-color: #fff8dc;
    --light-remove-highlight-color: #ffebee;
    --coverage-covered: #e0f2f1;
    --coverage-not-covered: #ffd1a4;

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
    --syntax-type-color: #2a66d9;
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
  }
</style></custom-style>`;

document.head.appendChild($_documentContainer.content);
