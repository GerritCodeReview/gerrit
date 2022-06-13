/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import './gr-diff-selection';
import {GrDiffSelection} from './gr-diff-selection';
import {createDiff} from '../../../test/test-data-generators';
import {DiffInfo, Side} from '../../../api/diff';
import {GrFormattedText} from '../../../elements/shared/gr-formatted-text/gr-formatted-text';
import {fixture, html} from '@open-wc/testing-helpers';
import {mouseDown} from '../../../test/test-utils';

const diffTableTemplate = html`
  <table id="diffTable" class="side-by-side">
    <tr class="diff-row">
      <td class="blame" data-line-number="1"></td>
      <td class="lineNum left" data-value="1">1</td>
      <td class="content">
        <div class="contentText" data-side="left">ba ba</div>
        <div data-side="left">
          <div class="comment-thread">
            <div class="gr-formatted-text message">
              <span id="output" class="gr-linked-text">This is a comment</span>
            </div>
          </div>
        </div>
      </td>
      <td class="lineNum right" data-value="1">1</td>
      <td class="content">
        <div class="contentText" data-side="right">some other text</div>
      </td>
    </tr>
    <tr class="diff-row">
      <td class="blame" data-line-number="2"></td>
      <td class="lineNum left" data-value="2">2</td>
      <td class="content">
        <div class="contentText" data-side="left">zin</div>
      </td>
      <td class="lineNum right" data-value="2">2</td>
      <td class="content">
        <div class="contentText" data-side="right">more more more</div>
        <div data-side="right">
          <div class="comment-thread">
            <div class="gr-formatted-text message">
              <span id="output" class="gr-linked-text"
                >This is a comment on the right</span
              >
            </div>
          </div>
        </div>
      </td>
    </tr>
    <tr class="diff-row">
      <td class="blame" data-line-number="3"></td>
      <td class="lineNum left" data-value="3">3</td>
      <td class="content">
        <div class="contentText" data-side="left">ga ga</div>
        <div data-side="left">
          <div class="comment-thread">
            <div class="gr-formatted-text message">
              <span id="output" class="gr-linked-text"
                >This is <a>a</a> different comment 💩 unicode is fun</span
              >
            </div>
          </div>
        </div>
      </td>
      <td class="lineNum right" data-value="3">3</td>
    </tr>
    <tr class="diff-row">
      <td class="blame" data-line-number="4"></td>
      <td class="lineNum left" data-value="4">4</td>
      <td class="content">
        <div class="contentText" data-side="left">ga ga</div>
        <div data-side="left">
          <div class="comment-thread">
            <textarea data-side="right">test for textarea copying</textarea>
          </div>
        </div>
      </td>
      <td class="lineNum right" data-value="4">4</td>
    </tr>
    <tr class="not-diff-row">
      <td class="other">
        <div class="contentText" data-side="right">some other text</div>
      </td>
    </tr>
  </table>
`;

suite('gr-diff-selection', () => {
  let element: GrDiffSelection;
  let diffTable: HTMLTableElement;

  const emulateCopyOn = function (target: HTMLElement | null) {
    const fakeEvent = {
      target,
      preventDefault: sinon.stub(),
      composedPath() {
        return [target];
      },
      clipboardData: {
        setData: sinon.stub(),
      },
    };
    element.handleCopy(fakeEvent as unknown as ClipboardEvent);
    return fakeEvent;
  };

  setup(async () => {
    element = new GrDiffSelection();
    diffTable = await fixture<HTMLTableElement>(diffTableTemplate);

    const diff: DiffInfo = {
      ...createDiff(),
      content: [
        {
          a: ['ba ba'],
          b: ['some other text'],
        },
        {
          a: ['zin'],
          b: ['more more more'],
        },
        {
          a: ['ga ga'],
          b: ['some other text'],
        },
      ],
    };
    element.init(diff, diffTable);
  });

  test('applies selected-left on left side click', () => {
    element.diffTable!.classList.add('selected-right');
    const lineNumberEl = diffTable.querySelector<HTMLElement>('.lineNum.left');
    if (!lineNumberEl) assert.fail('line number element missing');
    mouseDown(lineNumberEl);
    assert.isTrue(
      element.diffTable!.classList.contains('selected-left'),
      'adds selected-left'
    );
    assert.isFalse(
      element.diffTable!.classList.contains('selected-right'),
      'removes selected-right'
    );
  });

  test('applies selected-right on right side click', () => {
    element.diffTable!.classList.add('selected-left');
    const lineNumberEl = diffTable.querySelector<HTMLElement>('.lineNum.right');
    if (!lineNumberEl) assert.fail('line number element missing');
    mouseDown(lineNumberEl);
    assert.isTrue(
      element.diffTable!.classList.contains('selected-right'),
      'adds selected-right'
    );
    assert.isFalse(
      element.diffTable!.classList.contains('selected-left'),
      'removes selected-left'
    );
  });

  test('applies selected-blame on blame click', () => {
    element.diffTable!.classList.add('selected-left');
    const blameDiv = document.createElement('div');
    blameDiv.classList.add('blame');
    element.diffTable!.appendChild(blameDiv);
    mouseDown(blameDiv);
    assert.isTrue(
      element.diffTable!.classList.contains('selected-blame'),
      'adds selected-right'
    );
    assert.isFalse(
      element.diffTable!.classList.contains('selected-left'),
      'removes selected-left'
    );
  });

  test('ignores copy for non-content Element', () => {
    const getSelectedTextStub = sinon.stub(element, 'getSelectedText');
    emulateCopyOn(diffTable.querySelector('.not-diff-row'));
    assert.isFalse(getSelectedTextStub.called);
  });

  test('asks for text for left side Elements', () => {
    const getSelectedTextStub = sinon.stub(element, 'getSelectedText');
    emulateCopyOn(diffTable.querySelector('div.contentText'));
    assert.deepEqual([Side.LEFT, false], getSelectedTextStub.lastCall.args);
  });

  test('reacts to copy for content Elements', () => {
    const getSelectedTextStub = sinon.stub(element, 'getSelectedText');
    emulateCopyOn(diffTable.querySelector('div.contentText'));
    assert.isTrue(getSelectedTextStub.called);
  });

  test('copy event is prevented for content Elements', () => {
    const getSelectedTextStub = sinon.stub(element, 'getSelectedText');
    getSelectedTextStub.returns('test');
    const event = emulateCopyOn(diffTable.querySelector('div.contentText'));
    assert.isTrue(event.preventDefault.called);
  });

  test('inserts text into clipboard on copy', () => {
    sinon.stub(element, 'getSelectedText').returns('the text');
    const event = emulateCopyOn(diffTable.querySelector('div.contentText'));
    assert.deepEqual(
      ['Text', 'the text'],
      event.clipboardData.setData.lastCall.args
    );
  });

  test('setClasses adds given SelectionClass values, removes others', () => {
    element.diffTable!.classList.add('selected-right');
    element.setClasses(['selected-comment', 'selected-left']);
    assert.isTrue(element.diffTable!.classList.contains('selected-comment'));
    assert.isTrue(element.diffTable!.classList.contains('selected-left'));
    assert.isFalse(element.diffTable!.classList.contains('selected-right'));
    assert.isFalse(element.diffTable!.classList.contains('selected-blame'));

    element.setClasses(['selected-blame']);
    assert.isFalse(element.diffTable!.classList.contains('selected-comment'));
    assert.isFalse(element.diffTable!.classList.contains('selected-left'));
    assert.isFalse(element.diffTable!.classList.contains('selected-right'));
    assert.isTrue(element.diffTable!.classList.contains('selected-blame'));
  });

  test('setClasses removes before it ads', () => {
    element.diffTable!.classList.add('selected-right');
    const addStub = sinon.stub(element.diffTable!.classList, 'add');
    const removeStub = sinon
      .stub(element.diffTable!.classList, 'remove')
      .callsFake(() => {
        assert.isFalse(addStub.called);
      });
    element.setClasses(['selected-comment', 'selected-left']);
    assert.isTrue(addStub.called);
    assert.isTrue(removeStub.called);
  });

  test('copies content correctly', () => {
    element.diffTable!.classList.add('selected-left');
    element.diffTable!.classList.remove('selected-right');

    const selection = document.getSelection();
    if (selection === null) assert.fail('no selection');
    selection.removeAllRanges();
    const range = document.createRange();
    range.setStart(diffTable.querySelector('div.contentText')!.firstChild!, 3);
    range.setEnd(
      diffTable.querySelectorAll('div.contentText')[4]!.firstChild!,
      2
    );
    selection.addRange(range);
    assert.equal(element.getSelectedText(Side.LEFT, false), 'ba\nzin\nga');
  });

  test('copies comments', () => {
    element.diffTable!.classList.add('selected-left');
    element.diffTable!.classList.add('selected-comment');
    element.diffTable!.classList.remove('selected-right');
    const selection = document.getSelection();
    if (selection === null) assert.fail('no selection');
    selection.removeAllRanges();
    const range = document.createRange();
    range.setStart(
      diffTable.querySelector('.gr-formatted-text *')!.firstChild!,
      3
    );
    range.setEnd(
      diffTable.querySelectorAll('.gr-formatted-text *')[2].childNodes[2],
      7
    );
    selection.addRange(range);
    assert.equal(
      's is a comment\nThis is a differ',
      element.getSelectedText(Side.LEFT, true)
    );
  });

  test('respects astral chars in comments', () => {
    element.diffTable!.classList.add('selected-left');
    element.diffTable!.classList.add('selected-comment');
    element.diffTable!.classList.remove('selected-right');
    const selection = document.getSelection();
    if (selection === null) assert.fail('no selection');
    selection.removeAllRanges();
    const range = document.createRange();
    const nodes = diffTable.querySelectorAll('.gr-formatted-text *');
    range.setStart(nodes[2].childNodes[2], 13);
    range.setEnd(nodes[2].childNodes[2], 23);
    selection.addRange(range);
    assert.equal('mment 💩 u', element.getSelectedText(Side.LEFT, true));
  });

  test('defers to default behavior for textarea', () => {
    element.diffTable!.classList.add('selected-left');
    element.diffTable!.classList.remove('selected-right');
    const selectedTextSpy = sinon.spy(element, 'getSelectedText');
    emulateCopyOn(diffTable.querySelector('textarea'));
    assert.isFalse(selectedTextSpy.called);
  });

  test('regression test for 4794', () => {
    element.diffTable!.classList.add('selected-right');
    element.diffTable!.classList.remove('selected-left');

    const selection = document.getSelection();
    if (!selection) assert.fail('no selection');
    selection.removeAllRanges();
    const range = document.createRange();
    range.setStart(
      diffTable.querySelectorAll('div.contentText')[1]!.firstChild!,
      4
    );
    range.setEnd(
      diffTable.querySelectorAll('div.contentText')[1]!.firstChild!,
      10
    );
    selection.addRange(range);
    assert.equal(element.getSelectedText(Side.RIGHT, false), ' other');
  });

  test('copies to end of side (issue 7895)', () => {
    element.diffTable!.classList.add('selected-left');
    element.diffTable!.classList.remove('selected-right');
    const selection = document.getSelection();
    if (selection === null) assert.fail('no selection');
    selection.removeAllRanges();
    const range = document.createRange();
    range.setStart(diffTable.querySelector('div.contentText')!.firstChild!, 3);
    range.setEnd(
      diffTable.querySelectorAll('div.contentText')[4]!.firstChild!,
      2
    );
    selection.addRange(range);
    assert.equal(element.getSelectedText(Side.LEFT, false), 'ba\nzin\nga');
  });

  suite('getTextContentForRange', () => {
    let selection: Selection;
    let range: Range;
    let nodes: NodeListOf<GrFormattedText>;

    setup(() => {
      element.diffTable!.classList.add('selected-left');
      element.diffTable!.classList.add('selected-comment');
      element.diffTable!.classList.remove('selected-right');
      const s = document.getSelection();
      if (s === null) assert.fail('no selection');
      selection = s;
      selection.removeAllRanges();
      range = document.createRange();
      nodes = diffTable.querySelectorAll('.gr-formatted-text *');
    });

    test('multi level element contained in range', () => {
      range.setStart(nodes[2].childNodes[0], 1);
      range.setEnd(nodes[2].childNodes[2], 7);
      selection.addRange(range);
      assert.equal(
        element.getTextContentForRange(diffTable, selection, range),
        'his is a differ'
      );
    });

    test('multi level element as startContainer of range', () => {
      range.setStart(nodes[2].childNodes[1], 0);
      range.setEnd(nodes[2].childNodes[2], 7);
      selection.addRange(range);
      assert.equal(
        element.getTextContentForRange(diffTable, selection, range),
        'a differ'
      );
    });

    test('startContainer === endContainer', () => {
      range.setStart(nodes[0].firstChild!, 2);
      range.setEnd(nodes[0].firstChild!, 12);
      selection.addRange(range);
      assert.equal(
        element.getTextContentForRange(diffTable, selection, range),
        'is is a co'
      );
    });
  });
});
