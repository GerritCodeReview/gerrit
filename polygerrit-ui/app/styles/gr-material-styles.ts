/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const materialStyles = css`
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
    --md-focus-ring-duration: 0s;
    --md-switch-handle-height: 12px;
    --md-switch-handle-width: 12px;
    --md-switch-selected-handle-height: 12px;
    --md-switch-selected-handle-width: 12px;
    --md-switch-pressed-handle-height: 12px;
    --md-switch-pressed-handle-width: 12px;
    --md-switch-track-height: 16px;
    --md-switch-track-outline-width: 0;
    --md-switch-track-width: 32px;
    --md-switch-state-layer-size: 32px;
    padding-right: 0.5em;
  }

  md-tabs {
    font-size: var(--font-size-h3);
    font-weight: var(--font-weight-h3);
    line-height: var(--line-height-h3);
    --md-sys-typescale-title-small-font: var(--header-font-family);
    --md-sys-typescale-title-small-size: var(--font-size-h3);
    --md-sys-typescale-title-small-line-height: var(--line-height-h3);
    --md-sys-color-on-surface: var(--tabs-color-on-surface);
    --md-secondary-tab-active-hover-state-layer-color: var(
      --deemphasized-text-color
    );
    --md-sys-color-on-surface-variant: var(--deemphasized-text-color);
    --md-sys-color-primary: var(--link-color);
    --md-sys-color-surface: transparent;
  }
  md-tabs::part(divider) {
    display: none;
  }

  md-outlined-text-field {
    background-color: var(--view-background-color);
    color: var(--primary-text-color);
    --md-sys-color-primary: var(--primary-text-color);
    --md-sys-color-on-surface: var(--primary-text-color);
    --md-sys-color-on-surface-variant: var(--deemphasized-text-color);
    --md-outlined-text-field-label-text-color: var(--deemphasized-text-color);
    --md-outlined-text-field-focus-label-text-color: var(
      --deemphasized-text-color
    );
    --md-outlined-text-field-hover-label-text-color: var(
      --deemphasized-text-color
    );
    border-radius: var(--border-radius);
    --md-outlined-text-field-container-shape: var(--border-radius);
    --md-outlined-text-field-focus-outline-color: var(
      --prominent-border-color,
      var(--border-color)
    );
    --md-outlined-text-field-outline-color: var(
      --prominent-border-color,
      var(--border-color)
    );
    --md-outlined-text-field-hover-outline-color: var(
      --prominent-border-color,
      var(--border-color)
    );
    --md-sys-color-outline: var(--prominent-border-color, var(--border-color));
    --md-outlined-field-top-space: var(--spacing-s);
    --md-outlined-field-bottom-space: var(--spacing-s);
    --md-outlined-text-field-outline-width: 1px;
    --md-outlined-text-field-hover-outline-width: 1px;
    --md-outlined-text-field-focus-outline-width: 0;
    --md-outlined-field-leading-space: 8px;
  }

  md-outlined-text-field.showBlueFocusBorder {
    --md-outlined-text-field-focus-outline-width: 2px;
    --md-outlined-text-field-focus-outline-color: var(
      --input-focus-border-color
    );
  }
`;

const $_documentContainer = document.createElement('template');
$_documentContainer.innerHTML = `<dom-module id="gr-material-styles">
  <template>
    <style>
    ${materialStyles.cssText}
    </style>
  </template>
</dom-module>`;
document.head.appendChild($_documentContainer.content);
