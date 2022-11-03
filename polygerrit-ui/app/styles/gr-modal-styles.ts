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

    /*
     * IE has shoddy support for the font shorthand property.
     * Work around this using font-size and font-family.
     */
    -webkit-text-size-adjust: none;
    font-family: var(--font-family, ''), 'Roboto', Arial, sans-serif;
    font-size: var(--font-size-normal, 1rem);
    line-height: var(--line-height-normal, 1.4);
    color: var(--primary-text-color, black);
    height: 100%;
    transition: none; /* Override the default Polymer fade-in. */
  }

  dialog::backdrop {
    background-color: black;
    opacity: var(--modal-opacity, 0.6);
  }
`;
