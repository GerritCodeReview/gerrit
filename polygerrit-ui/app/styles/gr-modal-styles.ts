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
  }
  dialog::backdrop {
    background-color: black;
    opacity: var(--modal-opacity, 0.6);
  }
`;
