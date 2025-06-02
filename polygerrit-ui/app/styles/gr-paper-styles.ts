/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const paperStyles = css`
  md-switch {
    --md-sys-color-background: var(--switch-color-background);
    --md-sys-color-on-background: var(--switch-color-on-background);
    --md-sys-color-surface: var(--switch-color-surface);
    --md-sys-color-surface-dim: var(--switch-color-surface-dim);
    --md-sys-color-surface-bright: var(--switch-color-surface-bright);
    --md-sys-color-surface-container-lowest: var(
      --switch-color-surface-container-lowest
    );
    --md-sys-color-surface-container-low: var(
      --switch-color-surface-container-low
    );
    --md-sys-color-surface-container: var(--switch-color-surface-container);
    --md-sys-color-surface-container-high: var(
      --switch-color-surface-container-high
    );
    --md-sys-color-surface-container-highest: var(
      --switch-color-surface-container-highest
    );
    --md-sys-color-on-surface: var(--switch-color-on-surface);
    --md-sys-color-surface-variant: var(--switch-color-surface-variant);
    --md-sys-color-on-surface-variant: var(--switch-color-on-surface-variant);
    --md-sys-color-inverse-surface: var(--switch-color-inverse-surface);
    --md-sys-color-inverse-on-surface: var(--switch-color-inverse-on-surface);
    --md-sys-color-outline: var(--switch-color-outline);
    --md-sys-color-outline-variant: var(--switch-color-outline-variant);
    --md-sys-color-shadow: var(--switch-color-shadow);
    --md-sys-color-scrim: var(--switch-color-scrim);
    --md-sys-color-surface-tint: var(--switch-color-surface-tint);
    --md-sys-color-primary: var(--switch-color-primary);
    --md-sys-color-on-primary: var(--switch-color-on-primary);
    --md-sys-color-primary-container: var(--switch-color-primary-container);
    --md-sys-color-on-primary-container: var(
      --switch-color-on-primary-container
    );
    --md-sys-color-inverse-primary: var(--switch-color-inverse-primary);
    --md-sys-color-secondary: var(--switch-color-secondary);
    --md-sys-color-on-secondary: var(--switch-color-on-secondary);
    --md-sys-color-secondary-container: var(--switch-color-secondary-container);
    --md-sys-color-on-secondary-container: var(
      --switch-color-on-secondary-container
    );
    --md-sys-color-tertiary: var(--switch-color-tertiary);
    --md-sys-color-on-tertiary: var(--switch-color-on-tertiary);
    --md-sys-color-tertiary-container: var(--switch-color-tertiary-container);
    --md-sys-color-on-tertiary-container: var(
      --switch-color-on-tertiary-container
    );
    --md-sys-color-error: var(--switch-color-error);
    --md-sys-color-on-error: var(--switch-color-on-error);
    --md-sys-color-error-container: var(--switch-color-error-container);
    --md-sys-color-on-error-container: var(--switch-color-on-error-container);

    padding-right: 0.5em;
  }

  paper-toggle-button {
    --paper-toggle-button-checked-bar-color: var(--link-color);
    --paper-toggle-button-checked-button-color: var(--link-color);
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
