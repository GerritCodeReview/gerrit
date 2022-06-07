/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const formStyles = css`
  .gr-form-styles input {
    background-color: var(--view-background-color);
    color: var(--primary-text-color);
  }
  .gr-form-styles select {
    background-color: var(--select-background-color);
    color: var(--primary-text-color);
  }
  .gr-form-styles h1,
  .gr-form-styles h2 {
    margin-bottom: var(--spacing-s);
  }
  .gr-form-styles h4 {
    font-weight: var(--font-weight-bold);
  }
  .gr-form-styles fieldset {
    border: none;
    margin-bottom: var(--spacing-xxl);
  }
  .gr-form-styles section {
    display: flex;
    margin: var(--spacing-s) 0;
    min-height: 2em;
  }
  .gr-form-styles section * {
    vertical-align: middle;
  }
  .gr-form-styles .title,
  .gr-form-styles .value {
    display: inline-block;
  }
  .gr-form-styles .title {
    color: var(--deemphasized-text-color);
    font-weight: var(--font-weight-bold);
    padding-right: var(--spacing-m);
    width: 15em;
  }
  .gr-form-styles th {
    color: var(--deemphasized-text-color);
    text-align: left;
    vertical-align: bottom;
  }
  .gr-form-styles td,
  .gr-form-styles tfoot th {
    padding: var(--spacing-s) 0;
    vertical-align: middle;
  }
  .gr-form-styles .emptyHeader {
    text-align: right;
  }
  .gr-form-styles table {
    width: 50em;
  }
  .gr-form-styles th:first-child,
  .gr-form-styles td:first-child {
    width: 15em;
  }
  .gr-form-styles th:first-child input,
  .gr-form-styles td:first-child input {
    width: 14em;
  }
  .gr-form-styles input:not([type='checkbox']),
  .gr-form-styles select,
  .gr-form-styles textarea {
    border: 1px solid var(--border-color);
    border-radius: var(--border-radius);
    padding: var(--spacing-s);
  }
  .gr-form-styles td:last-child {
    width: 5em;
  }
  .gr-form-styles th:last-child gr-button,
  .gr-form-styles td:last-child gr-button {
    width: 100%;
  }
  .gr-form-styles iron-autogrow-textarea {
    height: auto;
    min-height: 4em;
  }
  .gr-form-styles gr-autocomplete {
    width: 14em;
  }
  @media only screen and (max-width: 40em) {
    .gr-form-styles section {
      margin-bottom: var(--spacing-l);
    }
    .gr-form-styles .title,
    .gr-form-styles .value {
      display: block;
    }
    .gr-form-styles table {
      width: 100%;
    }
  }
`;

const $_documentContainer = document.createElement('template');
$_documentContainer.innerHTML = `<dom-module id="gr-form-styles">
  <template>
    <style>
    ${formStyles.cssText}
    </style>
  </template>
</dom-module>`;
document.head.appendChild($_documentContainer.content);
