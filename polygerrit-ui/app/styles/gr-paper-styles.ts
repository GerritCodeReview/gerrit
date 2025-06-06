/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const paperStyles = css`
  md-switch {
    --md-sys-color-surface-container-highest: var(
      --switch-color-surface-container-highest
    );
    --md-sys-color-on-surface: var(--switch-color-on-surface);
    --md-sys-color-on-surface-variant: var(--switch-color-on-surface-variant);
    --md-sys-color-outline: var(--switch-color-outline);
    --md-sys-color-primary: var(--switch-color-primary);
    --md-sys-color-on-primary: var(--switch-color-on-primary);
    --md-sys-color-primary-container: var(--switch-color-primary-container);

    padding-right: 0.5em;
  }

  /* prettier formatter removes semi-colons after css mixins. */
  /* prettier-ignore */
  paper-tabs {
    font-size: var(--font-size-h3);
    font-weight: var(--font-weight-h3);
    line-height: var(--line-height-h3);
    --paper-font-common-base: {
      font-family: var(--header-font-family);
      -webkit-font-smoothing: initial;
    };
    --paper-tab-content: {
      margin-bottom: var(--spacing-s);
    };
    --paper-tab-content-focused: {
      /* paper-tabs uses 700 here, which can look awkward */
      font-weight: var(--font-weight-h3);
      background: var(--gray-background-focus);
    };
    --paper-tab-content-unselected: {
      /* paper-tabs uses 0.8 here, but we want to control the color directly */
      opacity: 1;
      color: var(--deemphasized-text-color);
    };
  }
  paper-tab:focus {
    padding-left: 0px;
    padding-right: 0px;
  }
`;

const $_documentContainer = document.createElement('template');
$_documentContainer.innerHTML = `<dom-module id="gr-paper-styles">
  <template>
    <style>
    ${paperStyles.cssText}
    </style>
  </template>
</dom-module>`;
document.head.appendChild($_documentContainer.content);
