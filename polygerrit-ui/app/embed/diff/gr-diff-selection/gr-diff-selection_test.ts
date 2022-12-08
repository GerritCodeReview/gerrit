/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-diff-selection';
import '../gr-diff/gr-diff';
import '../../../elements/shared/gr-comment-thread/gr-comment-thread';
import {GrDiffSelection} from './gr-diff-selection';
import {
  createComment,
  createDiff,
  createThread,
} from '../../../test/test-data-generators';
import {DiffInfo, Side} from '../../../api/diff';
import {GrFormattedText} from '../../../elements/shared/gr-formatted-text/gr-formatted-text';
import {fixture, html, assert} from '@open-wc/testing';
import {mouseDown, queryAndAssert} from '../../../test/test-utils';
import {GrDiff} from '../gr-diff/gr-diff';
import {waitForEventOnce} from '../../../utils/event-util';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {CommentThread} from '../../../utils/comment-util';
import {LitElement} from 'lit';

function createThreadEl(thread: CommentThread, side: Side) {
  const threadEl = document.createElement('gr-comment-thread');
  threadEl.className = 'comment-thread';
  threadEl.rootId = thread.rootId;
  threadEl.thread = thread;
  threadEl.showPatchset = false;
  threadEl.showPortedComment = !!thread.ported;
  // These attributes are the "interface" between comment threads and gr-diff.
  // <gr-comment-thread> does not care about them and is not affected by them.
  threadEl.setAttribute('slot', `${side}-${thread.line || 'LOST'}`);
  threadEl.setAttribute('diff-side', `${side}`);
  threadEl.setAttribute('line-num', `${thread.line || 'LOST'}`);
  if (thread.range) {
    threadEl.setAttribute('range', `${JSON.stringify(thread.range)}`);
  }
  return threadEl;
}

suite('gr-diff-selection', () => {
  let element: GrDiffSelection;
  let diffTable: HTMLElement;
  let grDiff: GrDiff;

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
    grDiff = await fixture<GrDiff>(html`<gr-diff></gr-diff>`);
    element = grDiff.diffSelection;

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
    grDiff.prefs = createDefaultDiffPrefs();
    grDiff.diff = diff;
    grDiff.appendChild(
      createThreadEl({...createThread(createComment()), line: 1}, Side.LEFT)
    );
    grDiff.appendChild(
      createThreadEl({...createThread(createComment()), line: 2}, Side.RIGHT)
    );
    grDiff.appendChild(
      createThreadEl({...createThread(createComment()), line: 3}, Side.LEFT)
    );
    await waitForEventOnce(grDiff, 'render');
    assert.isOk(element.diffTable);
    diffTable = element.diffTable!;
  });

  test('applies selected-left on left side click', () => {
    diffTable.classList.add('selected-right');
    const lineNumberEl = diffTable.querySelector<HTMLElement>('.lineNum.left');
    if (!lineNumberEl) assert.fail('line number element missing');
    mouseDown(lineNumberEl);
    assert.isTrue(
      diffTable.classList.contains('selected-left'),
      'adds selected-left'
    );
    assert.isFalse(
      diffTable.classList.contains('selected-right'),
      'removes selected-right'
    );
  });

  test('applies selected-right on right side click', () => {
    diffTable.classList.add('selected-left');
    const lineNumberEl = diffTable.querySelector<HTMLElement>('.lineNum.right');
    if (!lineNumberEl) assert.fail('line number element missing');
    mouseDown(lineNumberEl);
    assert.isTrue(
      diffTable.classList.contains('selected-right'),
      'adds selected-right'
    );
    assert.isFalse(
      diffTable.classList.contains('selected-left'),
      'removes selected-left'
    );
  });

  test('applies selected-blame on blame click', () => {
    diffTable.classList.add('selected-left');
    const blameDiv = document.createElement('div');
    blameDiv.classList.add('blame');
    diffTable.appendChild(blameDiv);
    mouseDown(blameDiv);
    assert.isTrue(
      diffTable.classList.contains('selected-blame'),
      'adds selected-right'
    );
    assert.isFalse(
      diffTable.classList.contains('selected-left'),
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
    diffTable.classList.add('selected-right');
    element.setClasses(['selected-comment', 'selected-left']);
    assert.isTrue(diffTable.classList.contains('selected-comment'));
    assert.isTrue(diffTable.classList.contains('selected-left'));
    assert.isFalse(diffTable.classList.contains('selected-right'));
    assert.isFalse(diffTable.classList.contains('selected-blame'));

    element.setClasses(['selected-blame']);
    assert.isFalse(diffTable.classList.contains('selected-comment'));
    assert.isFalse(diffTable.classList.contains('selected-left'));
    assert.isFalse(diffTable.classList.contains('selected-right'));
    assert.isTrue(diffTable.classList.contains('selected-blame'));
  });

  test('setClasses removes before it ads', () => {
    diffTable.classList.add('selected-right');
    const addStub = sinon.stub(diffTable.classList, 'add');
    const removeStub = sinon
      .stub(diffTable.classList, 'remove')
      .callsFake(() => {
        assert.isFalse(addStub.called);
      });
    element.setClasses(['selected-comment', 'selected-left']);
    assert.isTrue(addStub.called);
    assert.isTrue(removeStub.called);
  });

  test('copies content correctly', () => {
    diffTable.classList.add('selected-left');
    diffTable.classList.remove('selected-right');

    const selection = document.getSelection();
    if (selection === null) assert.fail('no selection');
    selection.removeAllRanges();
    const range = document.createRange();
    range.setStart(
      diffTable.querySelector('div.contentText')!.firstChild!.firstChild!,
      3
    );
    range.setEnd(
      diffTable.querySelectorAll('div.contentText')[4]!.firstChild!.firstChild!,
      2
    );
    selection.addRange(range);
    assert.equal(element.getSelectedText(Side.LEFT, false), 'ba\nzin\nga');
  });

  test.only('copies comments', async () => {
    diffTable.classList.add('selected-left');
    diffTable.classList.add('selected-comment');
    diffTable.classList.remove('selected-right');
    const selection = document.getSelection();
    if (selection === null) assert.fail('no selection');
    selection.removeAllRanges();
    const range = document.createRange();
    const slots = [...diffTable.querySelectorAll<HTMLSlotElement>('slot')];
    const slot1 = slots.find(s => s.getAttribute('name') === 'left-1');
    const slot3 = slots.find(s => s.getAttribute('name') === 'left-3');
    assert.isOk(slot1);
    assert.equal(slot1?.assignedElements().length, 1);
    const commentThreadEl1 = slot1?.assignedElements()[0] as LitElement;
    const commentEl1 = queryAndAssert(commentThreadEl1, 'gr-comment');
    range.setStart(
      queryAndAssert(commentEl1, '.collapsedContent').firstChild!,
      3
    );
    const commentThreadEl3 = slot3?.assignedElements()[0] as LitElement;
    const commentEl3 = queryAndAssert(commentThreadEl3, 'gr-comment');
    range.setEnd(
      queryAndAssert(commentEl3, '.collapsedContent').firstChild!,
      7
    );
    selection.addRange(range);
    assert.equal(
      element.getSelectedText(Side.LEFT, true),
      's is a comment\nThis is a differ'
    );
  });

  test('respects astral chars in comments', () => {
    diffTable.classList.add('selected-left');
    diffTable.classList.add('selected-comment');
    diffTable.classList.remove('selected-right');
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
    diffTable.classList.add('selected-left');
    diffTable.classList.remove('selected-right');
    const selectedTextSpy = sinon.spy(element, 'getSelectedText');
    emulateCopyOn(diffTable.querySelector('textarea'));
    assert.isFalse(selectedTextSpy.called);
  });

  test('regression test for 4794', () => {
    diffTable.classList.add('selected-right');
    diffTable.classList.remove('selected-left');

    const selection = document.getSelection();
    if (!selection) assert.fail('no selection');
    selection.removeAllRanges();
    const range = document.createRange();
    range.setStart(
      diffTable.querySelectorAll('div.contentText')[1]!.firstChild!.firstChild!,
      4
    );
    range.setEnd(
      diffTable.querySelectorAll('div.contentText')[1]!.firstChild!.firstChild!,
      10
    );
    selection.addRange(range);
    assert.equal(element.getSelectedText(Side.RIGHT, false), ' other');
  });

  test('copies to end of side (issue 7895)', () => {
    diffTable.classList.add('selected-left');
    diffTable.classList.remove('selected-right');
    const selection = document.getSelection();
    if (selection === null) assert.fail('no selection');
    selection.removeAllRanges();
    const range = document.createRange();
    range.setStart(
      diffTable.querySelector('div.contentText')!.firstChild!.firstChild!,
      3
    );
    range.setEnd(
      diffTable.querySelectorAll('div.contentText')[4]!.firstChild!.firstChild!,
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
      diffTable.classList.add('selected-left');
      diffTable.classList.add('selected-comment');
      diffTable.classList.remove('selected-right');
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
        element.getTextContentForRange(grDiff, selection, range),
        'his is a differ'
      );
    });

    test('multi level element as startContainer of range', () => {
      range.setStart(nodes[2].childNodes[1], 0);
      range.setEnd(nodes[2].childNodes[2], 7);
      selection.addRange(range);
      assert.equal(
        element.getTextContentForRange(grDiff, selection, range),
        'a differ'
      );
    });

    test('startContainer === endContainer', () => {
      range.setStart(nodes[0].firstChild!, 2);
      range.setEnd(nodes[0].firstChild!, 12);
      selection.addRange(range);
      assert.equal(
        element.getTextContentForRange(grDiff, selection, range),
        'is is a co'
      );
    });
  });
});
