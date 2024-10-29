/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../test/common-test-setup';
import './gr-checks-results';
import './gr-checks-fix-preview';
import {html} from 'lit';
import {fixture, assert} from '@open-wc/testing';
import {createCheckFix} from '../../test/test-data-generators';
import {GrChecksFixPreview} from './gr-checks-fix-preview';
import {rectifyFix} from '../../models/checks/checks-util';
import {
  MockPromise,
  mockPromise,
  queryAndAssert,
  stubRestApi,
} from '../../test/test-utils';
import {NumericChangeId, PatchSetNumber, RepoName} from '../../api/rest-api';
import {FilePathToDiffInfoMap} from '../../types/common';
import {GrSuggestionDiffPreview} from '../shared/gr-suggestion-diff-preview/gr-suggestion-diff-preview';

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
        <gr-suggestion-diff-preview></gr-suggestion-diff-preview>
      `
    );
  });

  test('show-fix', async () => {
    element.previewLoaded = true;
    await element.updateComplete;
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
    element.previewLoaded = true;
    await element.updateComplete;
    const diffPreview = queryAndAssert<GrSuggestionDiffPreview>(
      element,
      'gr-suggestion-diff-preview'
    );
    const applyFixSpy = sinon.spy(diffPreview, 'applyFix');
    stubRestApi('applyFixSuggestion').returns(
      Promise.resolve({ok: true} as Response)
    );

    const button = queryAndAssert<HTMLElement>(element, 'gr-button.applyFix');
    assert.isFalse(button.hasAttribute('disabled'));
    button.click();

    assert.isTrue(applyFixSpy.called);
  });
});
