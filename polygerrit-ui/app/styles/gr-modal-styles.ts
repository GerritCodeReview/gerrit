/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const modalStyles = css`
  dialog {
    padding: 0;
    border: 1px solid var(--border-color);
    border-radius: var(--border-radius);
    background: var(--dialog-background-color);
    box-shadow: var(--elevation-level-5);

    /*
     * These styles are taken from main.css
     */

    font-family: var(--font-family, ''), 'Roboto', Arial, sans-serif;
    font-size: var(--font-size-normal, 1rem);
    line-height: var(--line-height-normal, 1.4);
    color: var(--primary-text-color, black);
  }

  dialog::backdrop {
    background-color: black;
    opacity: var(--modal-opacity, 0.6);
  }
`;
