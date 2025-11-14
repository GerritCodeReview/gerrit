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
    --md-switch-disabled-track-color: var(--switch-disabled-track-color);
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

  md-checkbox {
    background-color: var(--background-color-primary);
    --md-sys-color-primary: var(--checkbox-primary);
    --md-sys-color-on-primary: var(--checkbox-on-primary);
    --md-sys-color-on-surface: var(--checkbox-on-surface);
    --md-sys-color-on-surface-variant: var(--checkbox-on-surface-variant);
    --md-checkbox-container-shape: 0px;
  }

  md-radio {
    --md-sys-color-primary: var(--radio-primary);
    --md-sys-color-on-primary: var(--radio-on-primary);
    --md-sys-color-on-surface: var(--radio-on-surface);
    --md-sys-color-on-surface-variant: var(--radio-on-surface-variant);
  }

  md-outlined-select {
    min-width: unset;
    --md-outlined-field-top-space: 4px;
    --md-outlined-field-bottom-space: 4px;
    --md-sys-color-surface-container: var(--select-surface-container);
    --md-sys-color-on-secondary-container: var(--select-on-secondary-container);
    --md-sys-color-secondary-container: var(--select-secondary-container);
    --md-sys-color-primary: var(--select-primary);
    --md-sys-color-on-surface: var(--select-on-surface);
  }
`;
