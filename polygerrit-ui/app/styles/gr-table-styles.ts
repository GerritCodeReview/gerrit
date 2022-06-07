/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {css} from 'lit';

export const tableStyles = css`
  .genericList {
    background-color: var(--background-color-primary);
    border-collapse: collapse;
    width: 100%;
  }
  .genericList th,
  .genericList td {
    padding: var(--spacing-m) 0;
    vertical-align: middle;
  }
  .genericList tr {
    border-bottom: 1px solid var(--border-color);
  }
  .genericList tr:hover {
    background-color: var(--hover-background-color);
  }
  .genericList th {
    white-space: nowrap;
  }
  .genericList th,
  .genericList td {
    padding-right: var(--spacing-l);
  }
  .genericList tr th:first-of-type,
  .genericList tr td:first-of-type {
    padding-left: var(--spacing-l);
  }
  .genericList tr:first-of-type {
    border-top: 1px solid var(--border-color);
  }
  .genericList tr th:last-of-type,
  .genericList tr td:last-of-type {
    border-left: 1px solid var(--border-color);
    text-align: center;
    padding-left: var(--spacing-l);
  }
  .genericList tr th.delete,
  .genericList tr td.delete {
    padding-top: 0;
    padding-bottom: 0;
  }
  .genericList tr th.delete,
  .genericList tr td.delete,
  .genericList tr.loadingMsg td,
  .genericList tr.groupHeader td {
    border-left: none;
  }
  .genericList .loading {
    border: none;
    display: none;
  }
  .genericList td {
    flex-shrink: 0;
  }
  .genericList .topHeader,
  .genericList .groupHeader {
    color: var(--primary-text-color);
    font-weight: var(--font-weight-bold);
    text-align: left;
    vertical-align: middle;
  }
  .genericList .groupHeader {
    background-color: var(--background-color-secondary);
    font-family: var(--header-font-family);
    font-size: var(--font-size-h3);
    font-weight: var(--font-weight-h3);
    line-height: var(--line-height-h3);
  }
  .genericList a {
    color: var(--primary-text-color);
    text-decoration: none;
  }
  .genericList a:hover {
    text-decoration: underline;
  }
  .genericList .description {
    width: 99%;
  }
  .genericList .loadingMsg {
    color: var(--deemphasized-text-color);
    display: block;
    padding: var(--spacing-s) var(--spacing-l);
  }
  .genericList .loadingMsg:not(.loading) {
    display: none;
  }
`;

const $_documentContainer = document.createElement('template');
$_documentContainer.innerHTML = `
  <dom-module id="gr-table-styles">
    <template>
      <style>
      ${tableStyles.cssText}
      </style>
    </template>
  </dom-module>
`;
document.head.appendChild($_documentContainer.content);
