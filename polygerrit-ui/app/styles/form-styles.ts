/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';
import {grFormStyles} from './gr-form-styles';

export const formStyles = css`
  input {
    background-color: var(--background-color-primary);
    border: 1px solid var(--border-color);
    border-radius: var(--border-radius);
    box-sizing: border-box;
    color: var(--primary-text-color);
    margin: 0;
    padding: var(--spacing-s);
  }
  /* prettier formatter removes semi-colons after css mixins. */
  /* prettier-ignore */
  iron-autogrow-textarea {
    background-color: inherit;
    color: var(--primary-text-color);
    border: 1px solid var(--border-color);
    border-radius: var(--border-radius);
    padding: 0;
    box-sizing: border-box;
    /* iron-autogrow-textarea has a "-webkit-appearance: textarea" :host
        css rule, which prevents overriding the border color. Clear that. */
    -webkit-appearance: none;
    --iron-autogrow-textarea_-_box-sizing: border-box;
    --iron-autogrow-textarea_-_padding: var(--spacing-s);
  }
  input,
  textarea,
  select {
    font: inherit;
  }
`;

const $_documentContainer = document.createElement('template');
$_documentContainer.innerHTML = `<dom-module id="form-styles">
  <template>
    <style>
    ${grFormStyles.cssText}
    </style>
  </template>
</dom-module>`;
document.head.appendChild($_documentContainer.content);
