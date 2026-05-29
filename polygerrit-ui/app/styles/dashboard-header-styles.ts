/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const dashboardHeaderStyles = css`
  :host {
    background-color: var(--view-background-color);
    display: block;
    min-height: 9em;
    width: 100%;
  }
  gr-avatar {
    display: inline-block;
    height: 7em;
    left: 1em;
    margin: 1em;
    top: 1em;
    width: 7em;
  }
  .info {
    display: inline-block;
    padding: var(--spacing-l);
    vertical-align: top;
  }
  .info > div > span {
    display: inline-block;
    font-weight: var(--font-weight-medium);
    width: 3.5em;
  }
`;
