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
  a {
    color: var(--link-color);
  }
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
