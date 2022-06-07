/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const hovercardStyles = css`
  :host {
    position: absolute;
    display: none;
    z-index: 200;
    max-width: 600px;
    outline: none;
  }
  :host(.hovered) {
    display: block;
  }
  :host(.hide) {
    visibility: hidden;
  }
  /* You have to use a <div class="container"> in your hovercard in order
      to pick up this consistent styling. */
  #container {
    background: var(--dialog-background-color);
    border: 1px solid var(--border-color);
    border-radius: var(--border-radius);
    box-shadow: var(--elevation-level-5);
  }
`;

const $_documentContainer = document.createElement('template');
$_documentContainer.innerHTML = `<dom-module id="gr-hovercard-styles">
  <template>
    <style>
    ${hovercardStyles.cssText}
    </style>
  </template>
</dom-module>`;
document.head.appendChild($_documentContainer.content);
