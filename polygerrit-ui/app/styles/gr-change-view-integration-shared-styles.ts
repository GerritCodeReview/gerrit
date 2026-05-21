/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {css} from 'lit';

/**
 * Shared styles for change-view integration.
 * This provides the core styling and overrides used by external
 * plugins or components that integrate closely with the Change View.
 */
export const changeViewIntegrationStyles = css`
  :host {
    border-top: 1px solid var(--border-color);
    display: block;
  }
  .header {
    color: var(--primary-text-color);
    background-color: var(--table-header-background-color);
    justify-content: space-between;
    padding: var(--spacing-m) var(--spacing-l);
    border-bottom: 1px solid var(--border-color);
  }
  .header .label {
    font-family: var(--header-font-family);
    font-size: var(--font-size-h3);
    font-weight: var(--font-weight-h3);
    line-height: var(--line-height-h3);
    margin: 0 var(--spacing-l) 0 0;
  }
  .header .note {
    color: var(--deemphasized-text-color);
  }
  .content {
    background-color: var(--view-background-color);
  }
  .header a,
  .content a {
    color: var(--link-color);
  }
`;
