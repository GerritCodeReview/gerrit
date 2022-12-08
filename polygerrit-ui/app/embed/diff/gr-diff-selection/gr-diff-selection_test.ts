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
import {createDiff} from '../../../test/test-data-generators';
import {DiffInfo, Side} from '../../../api/diff';
import {fixture, html, assert} from '@open-wc/testing';
import {mouseDown} from '../../../test/test-utils';
import {GrDiff} from '../gr-diff/gr-diff';
import {waitForEventOnce} from '../../../utils/event-util';
import {createDefaultDiffPrefs} from '../../../constants/constants';

function firstTextNode(el: HTMLElement) {
  return [...el.childNodes].filter(node => node.nodeType === Node.TEXT_NODE)[0];
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
    grDiff.renderPrefs = {use_lit_components: true};
    grDiff.diff = diff;
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
    assert.deepEqual([Side.LEFT], getSelectedTextStub.lastCall.args);
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
    const texts = diffTable.querySelectorAll<HTMLElement>('gr-diff-text');
    range.setStart(firstTextNode(texts[0]), 3);
    range.setEnd(firstTextNode(texts[4]), 2);
    selection.addRange(range);

    assert.equal(element.getSelectedText(Side.LEFT), 'ba\nzin\nga');
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
    const texts = diffTable.querySelectorAll<HTMLElement>('gr-diff-text');
    range.setStart(firstTextNode(texts[1]), 4);
    range.setEnd(firstTextNode(texts[1]), 10);
    selection.addRange(range);

    assert.equal(element.getSelectedText(Side.RIGHT), ' other');
  });
});
