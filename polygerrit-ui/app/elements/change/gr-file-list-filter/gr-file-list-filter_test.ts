/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-file-list-filter';
import {GrFileListFilter} from './gr-file-list-filter';
import {queryAndAssert, queryAll} from '../../../test/test-utils';
import {assert, fixture, html} from '@open-wc/testing';
import {GrButton} from '../../shared/gr-button/gr-button';

suite('gr-file-list-filter tests', () => {
  let element: GrFileListFilter;

  setup(async () => {
    element = await fixture<GrFileListFilter>(
      html`<gr-file-list-filter></gr-file-list-filter>`
    );
    element.allFileExtensions = ['css', 'ts'];
    element.fileExtensionCounts = {ts: 5, css: 2};
    element.hiddenFileExtensions = [];
    await element.updateComplete;
  });

  test('render when inactive', () => {
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
            <div class="dropdown-panel">
              <div class="dropdown-header">
                <span>Filter by extension</span>
                <div class="filter-actions">
                  <gr-button
                    aria-disabled="false"
                    class="selectAllBtn"
                    link=""
                    role="button"
                    tabindex="0"
                  >
                    All
                  </gr-button>
                  <span class="action-separator">|</span>
                  <gr-button
                    aria-disabled="false"
                    class="deselectAllBtn"
                    link=""
                    role="button"
                    tabindex="0"
                  >
                    None
                  </gr-button>
                </div>
              </div>
              <div class="dropdown-content">
                <div class="filter-item">
                  <md-checkbox checked="" touch-target="wrapper"></md-checkbox>
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
            </div>
          </md-menu>
        </span>
      `
    );
  });

  test('render when active', async () => {
    element.hiddenFileExtensions = ['css'];
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <span class="filterContainer">
          <gr-tooltip-content
            has-tooltip=""
            title="Filter files by extension (1 extension hidden)"
          >
            <gr-button
              aria-disabled="false"
              class="active filterBtn"
              id="filterBtn"
              link=""
              role="button"
              tabindex="0"
            >
              File Filter (1/2)
            </gr-button>
          </gr-tooltip-content>
          <md-menu
            aria-hidden="true"
            default-focus="none"
            id="filterMenu"
            quick=""
            tabindex="-1"
          >
            <div class="dropdown-panel">
              <div class="dropdown-header">
                <span>Filter by extension</span>
                <div class="filter-actions">
                  <gr-button
                    aria-disabled="false"
                    class="selectAllBtn"
                    link=""
                    role="button"
                    tabindex="0"
                  >
                    All
                  </gr-button>
                  <span class="action-separator">|</span>
                  <gr-button
                    aria-disabled="false"
                    class="deselectAllBtn"
                    link=""
                    role="button"
                    tabindex="0"
                  >
                    None
                  </gr-button>
                </div>
              </div>
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
            </div>
          </md-menu>
        </span>
      `
    );
  });

  test('selectAll button fires event with empty array', async () => {
    const listener = sinon.stub();
    element.addEventListener('hidden-file-extensions-changed', listener);

    queryAndAssert<GrButton>(element, '.selectAllBtn').click();
    await element.updateComplete;

    assert.isTrue(listener.calledOnce);
    assert.deepEqual(listener.lastCall.args[0].detail, {value: []});
  });

  test('deselectAll button fires event with all extensions', async () => {
    const listener = sinon.stub();
    element.addEventListener('hidden-file-extensions-changed', listener);

    queryAndAssert<GrButton>(element, '.deselectAllBtn').click();
    await element.updateComplete;

    assert.isTrue(listener.calledOnce);
    assert.deepEqual(listener.lastCall.args[0].detail, {value: ['css', 'ts']});
  });

  test('clicking filter-item row toggles extension', async () => {
    const listener = sinon.stub();
    element.addEventListener('hidden-file-extensions-changed', listener);

    const items = queryAll<HTMLElement>(element, '.filter-item');
    assert.equal(items.length, 2);

    items[0].click();
    await element.updateComplete;

    assert.isTrue(listener.calledOnce);
    assert.deepEqual(listener.lastCall.args[0].detail, {value: ['css']});
  });
});
