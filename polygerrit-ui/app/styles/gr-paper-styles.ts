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
  md-tabs {
    font-size: var(--font-size-h3);
    font-weight: var(--font-weight-h3);
    line-height: var(--line-height-h3);
    --md-sys-typescale-title-small-font: var(--header-font-family);
    --md-sys-typescale-title-small-size: var(--font-size-h3);
    --md-sys-typescale-title-small-line-height: var(--line-height-h3);
    --md-sys-color-on-surface: var(--tabs-color-on-surface);
    --md-secondary-tab-active-hover-state-layer-color: var(--deemphasized-text-color);
    --md-sys-color-on-surface-variant: var(--deemphasized-text-color);
    --md-sys-color-primary: var(--link-color);
    --md-sys-color-surface: transparent;
  }
  md-tabs::part(divider) {
    display: none;
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
