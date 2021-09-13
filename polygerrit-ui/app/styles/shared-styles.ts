/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    --iron-autogrow-textarea: {
      box-sizing: border-box;
      padding: var(--spacing-s);
    }
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
  paper-toggle-button {
    --paper-toggle-button-checked-bar-color: var(--link-color);
    --paper-toggle-button-checked-button-color: var(--link-color);
  }
  paper-tabs {
    font-size: var(--font-size-h3);
    font-weight: var(--font-weight-h3);
    line-height: var(--line-height-h3);
    --paper-font-common-base: {
      font-family: var(--header-font-family);
      -webkit-font-smoothing: initial;
    }
    --paper-tab-content: {
      margin-bottom: var(--spacing-s);
    }
    --paper-tab-content-focused: {
      /* paper-tabs uses 700 here, which can look awkward */
      font-weight: var(--font-weight-h3);
      background: var(--gray-background-focus);
    }
    --paper-tab-content-unselected: {
      /* paper-tabs uses 0.8 here, but we want to control the color directly */
      opacity: 1;
      color: var(--deemphasized-text-color);
    }
  }
  paper-tab:focus {
    padding-left: 0px;
    padding-right: 0px;
  }
  iron-autogrow-textarea {
    /** This is needed for firefox */
    --iron-autogrow-textarea_-_white-space: pre-wrap;
  }

  .assistive-tech-only {
    user-select: none;
    clip: rect(1px, 1px, 1px, 1px);
    height: 1px;
    margin: 0;
    overflow: hidden;
    padding: 0;
    position: absolute;
    white-space: nowrap;
    width: 1px;
    z-index: -1000;
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
