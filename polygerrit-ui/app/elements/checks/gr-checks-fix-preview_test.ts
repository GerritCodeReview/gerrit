/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../test/common-test-setup';
import './gr-checks-results';
import {html} from 'lit';
import {fixture, assert} from '@open-wc/testing';
import {createCheckFix, createDiff} from '../../test/test-data-generators';
import {GrChecksFixPreview} from './gr-checks-fix-preview';
import {rectifyFix} from '../../models/checks/checks-util';
import {
  MockPromise,
  mockPromise,
  queryAndAssert,
  stubRestApi,
  waitUntil,
} from '../../test/test-utils';
import {NumericChangeId, PatchSetNumber, RepoName} from '../../api/rest-api';
import {FilePathToDiffInfoMap} from '../../types/common';
import {testResolver} from '../../test/common-test-setup';
import {navigationToken} from '../core/gr-navigation/gr-navigation';

suite('gr-checks-fix-preview test', () => {
  let element: GrChecksFixPreview;
  let promise: MockPromise<FilePathToDiffInfoMap | undefined>;

  setup(async () => {
    promise = mockPromise<FilePathToDiffInfoMap | undefined>();
    stubRestApi('getFixPreview').returns(promise);

    const fix = rectifyFix(createCheckFix(), 'test-checker');
    element = await fixture<GrChecksFixPreview>(
      html`<gr-checks-fix-preview></gr-checks-fix-preview>`
    );
    await element.updateComplete;

    element.changeNum = 123 as NumericChangeId;
    element.patchSet = 5 as PatchSetNumber;
    element.latestPatchNum = 5 as PatchSetNumber;
    element.repo = 'test-repo' as RepoName;
    element.fixSuggestionInfo = fix;
    await element.updateComplete;
  });

  const loadDiff = async () => {
    promise.resolve({'foo.c': createDiff()});
    await waitUntil(() => !!element.diff);
  };

  test('renders loading', async () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="header">
          <div class="title">
            <span> Attached Fix </span>
          </div>
          <div>
            <gr-button
              class="showFix"
              aria-disabled="true"
              disabled=""
              flatten=""
              role="button"
              secondary=""
              tabindex="-1"
            >
              Show fix side-by-side
            </gr-button>
            <gr-button
              class="applyFix"
              aria-disabled="true"
              disabled=""
              flatten=""
              primary=""
              role="button"
              tabindex="-1"
              title="Fix is still loading ..."
            >
              Apply fix
            </gr-button>
          </div>
        </div>
        <div class="loading">Loading fix preview ...</div>
      `
    );
  });

  test('renders diff', async () => {
    await loadDiff();
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="header">
          <div class="title">
            <span> Attached Fix </span>
          </div>
          <div>
            <gr-button
              class="showFix"
              aria-disabled="false"
              flatten=""
              role="button"
              secondary=""
              tabindex="0"
            >
              Show fix side-by-side
            </gr-button>
            <gr-button
              class="applyFix"
              aria-disabled="false"
              flatten=""
              primary=""
              role="button"
              tabindex="0"
              title=""
            >
              Apply fix
            </gr-button>
          </div>
        </div>
        <div class="diff-container">
          <gr-diff
            class="disable-context-control-buttons hide-line-length-indicator"
            style="--line-limit-marker: 100ch; --content-width: none; --diff-max-width: none; --font-size: 12px;"
          >
          </gr-diff>
        </div>
      `
    );
  });

  test('show-fix', async () => {
    await loadDiff();

    const stub = sinon.stub();
    element.addEventListener('open-fix-preview', stub);

    const button = queryAndAssert<HTMLElement>(element, 'gr-button.showFix');
    assert.isFalse(button.hasAttribute('disabled'));
    button.click();

    assert.isTrue(stub.called);
    assert.deepEqual(stub.lastCall.args[0].detail, {
      patchNum: element.patchSet,
      fixSuggestions: [element.fixSuggestionInfo],
      onCloseFixPreviewCallbacks: [],
    });
  });

  test('apply-fix', async () => {
    await loadDiff();

    const setUrlSpy = sinon.stub(testResolver(navigationToken), 'setUrl');
    stubRestApi('applyFixSuggestion').returns(
      Promise.resolve({ok: true} as Response)
    );

    const button = queryAndAssert<HTMLElement>(element, 'gr-button.applyFix');
    assert.isFalse(button.hasAttribute('disabled'));
    button.click();

    await waitUntil(() => setUrlSpy.called);
    assert.equal(
      setUrlSpy.lastCall.args[0],
      '/c/test-repo/+/123/5..edit?forceReload=true'
    );
  });
});
