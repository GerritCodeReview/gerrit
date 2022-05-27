/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const sharedStyles = css`
  /* CSS reset */

  html,
  body,
  button,
  div,
  span,
  applet,
  object,
  iframe,
  h1,
  h2,
  h3,
  h4,
  h5,
  h6,
  p,
  blockquote,
  pre,
  a,
  abbr,
  acronym,
  address,
  big,
  cite,
  code,
  del,
  dfn,
  em,
  img,
  ins,
  kbd,
  q,
  s,
  samp,
  small,
  strike,
  strong,
  sub,
  sup,
  tt,
  var,
  b,
  u,
  i,
  center,
  dl,
  dt,
  dd,
  ol,
  ul,
  li,
  fieldset,
  form,
  label,
  legend,
  table,
  caption,
  tbody,
  tfoot,
  thead,
  tr,
  th,
  td,
  article,
  aside,
  canvas,
  details,
  embed,
  figure,
  figcaption,
  footer,
  header,
  hgroup,
  main,
  menu,
  nav,
  output,
  ruby,
  section,
  summary,
  time,
  mark,
  audio,
  video {
    border: 0;
    box-sizing: border-box;
    font-size: 100%;
    font: inherit;
    margin: 0;
    padding: 0;
    vertical-align: baseline;
  }
  *::after,
  *::before {
    box-sizing: border-box;
  }
  input {
    background-color: var(--background-color-primary);
    border: 1px solid var(--border-color);
    border-radius: var(--border-radius);
    box-sizing: border-box;
    color: var(--primary-text-color);
    margin: 0;
    padding: var(--spacing-s);
  }
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
  }
  /* We are putting css mixin defs in a separate rule, because they actually
     require a semi-colon at the end after the curly bracket, but
     auto-formatting removes that. */
  iron-autogrow-textarea {
    --iron-autogrow-textarea: {
      box-sizing: border-box;
      padding: var(--spacing-s);
    }
  }
  iron-autogrow-textarea {
    --iron-autogrow-textarea_-_box-sizing: border-box;
    --iron-autogrow-textarea_-_padding: var(--spacing-s);
    --iron-autogrow-textarea_-_white-space: pre-wrap;
  }
  a {
    color: var(--link-color);
  }
  input,
  textarea,
  select,
  button {
    font: inherit;
  }
  ol,
  ul {
    list-style: none;
  }
  blockquote,
  q {
    quotes: none;
  }
  blockquote:before,
  blockquote:after,
  q:before,
  q:after {
    content: '';
    content: none;
  }
  table {
    border-collapse: collapse;
    border-spacing: 0;
  }

  iron-icon {
    color: var(--deemphasized-text-color);
    vertical-align: top;
    --iron-icon-height: 20px;
    --iron-icon-width: 20px;
  }

  /* Stopgap solution until we remove hidden$ attributes. */

  :host([hidden]),
  [hidden] {
    display: none !important;
  }
  .separator {
    border-left: 1px solid var(--border-color);
    height: 20px;
    margin: 0 8px;
  }
  .separator.transparent {
    border-color: transparent;
  }

  /**
   * TODO: Remove these rules and change (plugin) users to rely on
   * gr-spinner-styles directly.
   */
  /** BEGIN: loading spiner */
  .loadingSpin {
    border: 2px solid var(--disabled-button-background-color);
    border-top: 2px solid var(--primary-button-background-color);
    border-radius: 50%;
    width: 10px;
    height: 10px;
    animation: spin 2s linear infinite;
    margin-right: var(--spacing-s);
  }
  @keyframes spin {
    0% {
      transform: rotate(0deg);
    }
    100% {
      transform: rotate(360deg);
    }
  }
  /** END: loading spiner */
`;

const $_documentContainer = document.createElement('template');
$_documentContainer.innerHTML = `<dom-module id="shared-styles">
  <template>
    <style>
    ${sharedStyles.cssText}
    </style>
  </template>
</dom-module>`;
document.head.appendChild($_documentContainer.content);
