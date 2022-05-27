/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html} from '@polymer/polymer/lib/utils/html-tag';

export const htmlTemplate = html`
  <style include="shared-styles">
    :host {
      background: var(--dialog-background-color);
      border-radius: var(--border-radius);
      box-shadow: var(--elevation-level-5);
    }

    @media screen and (max-width: 50em) {
      :host {
        height: 100%;
        left: 0;
        position: fixed;
        right: 0;
        top: 0;
        border-radius: 0;
        box-shadow: none;
      }
    }
  </style>
  <slot></slot>
`;
