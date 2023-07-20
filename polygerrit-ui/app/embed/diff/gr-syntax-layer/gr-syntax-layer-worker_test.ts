/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {DiffInfo, GrDiffLineType, Side} from '../../../api/diff';
import {
  HighlightService,
  highlightServiceToken,
} from '../../../services/highlight/highlight-service';
import '../../../test/common-test-setup';
import {testResolver} from '../../../test/common-test-setup';
import {mockPromise} from '../../../test/test-utils';
import {SyntaxLayerLine} from '../../../types/syntax-worker-api';
import {GrDiffLine} from '../gr-diff/gr-diff-line';
import {GrSyntaxLayerWorker} from './gr-syntax-layer-worker';

const diff: DiffInfo = {
  meta_a: {
    name: 'somepath/somefile.js',
    content_type: 'text/javascript',
    lines: 3,
    language: 'lang-left',
  },
  meta_b: {
    name: 'somepath/somefile.js',
    content_type: 'text/javascript',
    lines: 4,
    language: 'lang-right',
  },
  change_type: 'MODIFIED',
  intraline_status: 'OK',
  content: [
    {
      ab: ['import it;'],
    },
    {
      b: ['b only'],
    },
    {
      ab: ['  public static final {', 'ab3'],
    },
  ],
};

const leftRanges: SyntaxLayerLine[] = [
  {ranges: [{start: 0, length: 6, className: 'literal'}]},
  {ranges: []},
  {ranges: []},
];

const rightRanges: SyntaxLayerLine[] = [
  {ranges: []},
  {ranges: []},
  {
    ranges: [
      {start: 0, length: 2, className: 'not-safe'},
      {start: 2, length: 6, className: 'literal'},
      {start: 9, length: 6, className: 'keyword'},
      {start: 16, length: 5, className: 'name'},
    ],
  },
  {ranges: []},
];

suite('gr-syntax-layer-worker tests', () => {
  let layer: GrSyntaxLayerWorker;
  let listener: sinon.SinonStub;
  let highlightService: HighlightService;

  const annotate = (side: Side, lineNumber: number, text: string) => {
    const el = document.createElement('div');
    const lineNumberEl = document.createElement('td');
    el.setAttribute('data-side', side);
    el.innerText = text;
    const line = new GrDiffLine(GrDiffLineType.BOTH);
    if (side === Side.LEFT) line.beforeNumber = lineNumber;
    if (side === Side.RIGHT) line.afterNumber = lineNumber;
    layer.annotate(el, lineNumberEl, line);
    return el;
  };

  setup(() => {
    highlightService = testResolver(highlightServiceToken);
    layer = new GrSyntaxLayerWorker(() => highlightService);
  });

  test('cancel processing', async () => {
    const mockPromise1 = mockPromise<SyntaxLayerLine[]>();
    const mockPromise2 = mockPromise<SyntaxLayerLine[]>();
    const mockPromise3 = mockPromise<SyntaxLayerLine[]>();
    const mockPromise4 = mockPromise<SyntaxLayerLine[]>();
    const stub = sinon.stub(highlightService, 'highlight');
    stub.onCall(0).returns(mockPromise1);
    stub.onCall(1).returns(mockPromise2);
    stub.onCall(2).returns(mockPromise3);
    stub.onCall(3).returns(mockPromise4);

    const processPromise1 = layer.process(diff);
    // Calling the process() a second time means that the promises created
    // during the first call are cancelled.
    const processPromise2 = layer.process(diff);
    // We can await the outer promise even before the inner promises resolve,
    // because cancelling rejects the inner promises.
    await processPromise1;
    // It does not matter actually, whether these two inner promises are
    // resolved or not.
    mockPromise1.resolve(leftRanges);
    mockPromise2.resolve(rightRanges);
    // Both ranges must still be empty, because the promise of the first call
    // must have been cancelled and the returned ranges ignored.
    assert.isEmpty(layer.leftRanges);
    assert.isEmpty(layer.rightRanges);
    // Lets' resolve and await the promises of the second as normal.
    mockPromise3.resolve(leftRanges);
    mockPromise4.resolve(rightRanges);
    await processPromise2;
    assert.equal(layer.leftRanges, leftRanges);
  });

  suite('annotate and listen', () => {
    setup(() => {
      listener = sinon.stub();
      layer.addListener(listener);
      sinon.stub(highlightService, 'highlight').callsFake((lang?: string) => {
        if (lang === 'lang-left') return Promise.resolve(leftRanges);
        if (lang === 'lang-right') return Promise.resolve(rightRanges);
        return Promise.resolve([]);
      });
    });

    test('process and annotate line 2 LEFT', async () => {
      await layer.process(diff);
      const el = annotate(Side.LEFT, 1, 'import it;');
      assert.equal(
        el.innerHTML,
        '<hl class="gr-diff gr-syntax gr-syntax-literal">import</hl> it;'
      );
      assert.equal(listener.callCount, 2);
      assert.equal(listener.getCall(0).args[0], 1);
      assert.equal(listener.getCall(0).args[1], 1);
      assert.equal(listener.getCall(0).args[2], Side.LEFT);
      assert.equal(listener.getCall(1).args[0], 3);
      assert.equal(listener.getCall(1).args[1], 3);
      assert.equal(listener.getCall(1).args[2], Side.RIGHT);
    });

    test('process and annotate line 3 RIGHT', async () => {
      await layer.process(diff);
      const el = annotate(Side.RIGHT, 3, '  public static final {');
      assert.equal(
        el.innerHTML,
        '  <hl class="gr-diff gr-syntax gr-syntax-literal">public</hl> ' +
          '<hl class="gr-diff gr-syntax gr-syntax-keyword">static</hl> ' +
          '<hl class="gr-diff gr-syntax gr-syntax-name">final</hl> {'
      );
      assert.equal(listener.callCount, 2);
    });
  });
});
