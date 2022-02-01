/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {DiffInfo, GrDiffLineType, Side} from '../../../api/diff';
import '../../../test/common-test-setup-karma';
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
    layer = new GrSyntaxLayerWorker();
    listener = sinon.stub();
    layer.addListener(listener);
    sinon.stub(layer, 'highlight').callsFake((lang?: string) => {
      if (lang === 'lang-left') return Promise.resolve(leftRanges);
      if (lang === 'lang-right') return Promise.resolve(rightRanges);
      return Promise.resolve([]);
    });
    layer.init(diff);
  });

  test('process and annotate line 2 LEFT', async () => {
    await layer.process();
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
    await layer.process();
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
