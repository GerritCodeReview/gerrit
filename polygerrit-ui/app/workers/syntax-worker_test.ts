/**
 * @license
 * Copyright (C) 2026 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import {assert} from '@open-wc/testing';
import * as sinon from 'sinon';
import {
  SyntaxWorkerInit,
  SyntaxWorkerMessageType,
  SyntaxWorkerRequest,
} from '../types/syntax-worker-api';
import {SyntaxWorker} from './syntax-worker';

suite('syntax worker messages', () => {
  test('unknown languages fall back only for comment requests', () => {
    const highlight = sinon.stub();
    highlight.onFirstCall().throws(new Error('Unknown language'));
    highlight
      .onSecondCall()
      .returns({value: '<span class="keyword">const</span> x = 42;'});
    const getLanguage = sinon.stub();
    getLanguage.withArgs('js').returns({});
    const worker = new SyntaxWorker(() => {
      return {
        configure: sinon.stub(),
        getLanguage,
        highlight,
      };
    });
    const init: SyntaxWorkerInit = {
      type: SyntaxWorkerMessageType.INIT,
      url: 'highlight.js',
    };
    const request: SyntaxWorkerRequest = {
      type: SyntaxWorkerMessageType.REQUEST,
      language: 'missing-grammar',
      code: 'const x = 42;',
    };
    assert.deepEqual(worker.handleMessage(init), {ranges: []});
    const strict = worker.handleMessage(request);
    assert.equal(strict?.error, 'Unknown language');
    assert.deepEqual(strict?.ranges, []);
    const comment = {...request, ignoreUnknownLanguage: true};
    assert.deepEqual(worker.handleMessage(comment), {ranges: []});
    assert.isTrue(highlight.calledOnce);
    const known = {...request, language: 'js', ignoreUnknownLanguage: true};
    assert.includeDeepMembers(
      worker.handleMessage(known)?.ranges?.[0].ranges ?? [],
      [{className: 'keyword', start: 0, length: 5}]
    );
  });
});
