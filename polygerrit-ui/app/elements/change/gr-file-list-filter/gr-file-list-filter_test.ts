/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-file-list-filter';
import {GrFileListFilter} from './gr-file-list-filter';
import {fixture, html, assert} from '@open-wc/testing';

suite('gr-file-list-filter tests', () => {
  let element: GrFileListFilter;

  setup(async () => {
    element = await fixture<GrFileListFilter>(
      html`<gr-file-list-filter></gr-file-list-filter>`
    );
    element.allFileExtensions = ['css', 'ts'];
    element.fileExtensionCounts = {ts: 5, css: 2};
    element.visibleFileExtensions = ['ts'];
    await element.updateComplete;
  });

  test('render', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <span class="filterContainer">
          <gr-tooltip-content has-tooltip="" title="Filter files by extension">
            <gr-button
              aria-disabled="false"
              class="filterBtn"
              id="filterBtn"
              link=""
              role="button"
              tabindex="0"
            >
              File Filter
            </gr-button>
          </gr-tooltip-content>
          <md-menu
            aria-hidden="true"
            default-focus="none"
            id="filterMenu"
            quick=""
            tabindex="-1"
          >
            <div class="dropdown-content">
              <div class="filter-item">
                <md-checkbox touch-target="wrapper"></md-checkbox>
                <span>
                  css
                  <span class="ext-count"> (2) </span>
                </span>
              </div>
              <div class="filter-item">
                <md-checkbox checked="" touch-target="wrapper"></md-checkbox>
                <span>
                  ts
                  <span class="ext-count"> (5) </span>
                </span>
              </div>
            </div>
          </md-menu>
        </span>
      `
    );
  });
});
