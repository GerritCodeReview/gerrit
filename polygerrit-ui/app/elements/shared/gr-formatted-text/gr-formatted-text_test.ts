/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '../../../test/common-test-setup-karma';
import './gr-formatted-text';
import {
  GrFormattedText,
  Block,
  ListBlock,
  Paragraph,
  QuoteBlock,
  PreBlock,
  CodeBlock,
  InlineItem,
  ListItem,
  TextSpan,
  LinkSpan,
} from './gr-formatted-text';

const basicFixture = fixtureFromElement('gr-formatted-text');

suite('gr-formatted-text tests', () => {
  let element: GrFormattedText;

  function assertSpan(actual: InlineItem, expected: InlineItem) {
    assert.equal(actual.type, expected.type);
    assert.equal(actual.text, expected.text);
    switch (actual.type) {
      case 'link':
        assert.equal(actual.url, (expected as LinkSpan).url);
        break;
    }
  }

  function assertTextBlock(block: Block, spans: InlineItem[]) {
    assert.equal(block.type, 'paragraph');
    const paragraph = block as Paragraph;
    assert.equal(paragraph.spans.length, spans.length);
    for (let i = 0; i < paragraph.spans.length; ++i) {
      assertSpan(paragraph.spans[i], spans[i]);
    }
  }

  function assertPreBlock(block: Block, text: string) {
    assert.equal(block.type, 'pre');
    const preBlock = block as PreBlock;
    assert.equal(preBlock.text, text);
  }

  function assertCodeBlock(block: Block, text: string) {
    assert.equal(block.type, 'code');
    const preBlock = block as CodeBlock;
    assert.equal(preBlock.text, text);
  }

  function assertSimpleTextBlock(block: Block, text: string) {
    assertTextBlock(block, [{type: 'text', text}]);
  }

  function assertListBlock(block: Block, items: ListItem[]) {
    assert.equal(block.type, 'list');
    const listBlock = block as ListBlock;
    assert.deepEqual(listBlock.items, items);
  }

  function assertQuoteBlock(block: Block): QuoteBlock {
    assert.equal(block.type, 'quote');
    return block as QuoteBlock;
  }

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('parse empty', () => {
    assert.lengthOf(element._computeBlocks(''), 0);
  });

  for (const text of [
    'Para1',
    'Para 1\nStill para 1',
    'Para 1\n\nPara 2\n\nPara 3',
  ]) {
    test('parse simple', () => {
      const comment = {type: 'text', text} as TextSpan;
      const result = element._computeBlocks(text);
      assert.lengthOf(result, 1);
      assertTextBlock(result[0], [comment]);
    });
  }

  test('parse link', () => {
    const comment = '[text](url)';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertTextBlock(result[0], [{type: 'link', text: 'text', url: 'url'}]);
  });

  test('parse inline code', () => {
    const comment = 'text `code`';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertTextBlock(result[0], [
      {type: 'text', text: 'text '},
      {type: 'code', text: 'code'},
    ]);
  });

  test('parse quote', () => {
    const comment = '> Quote text';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    const quoteBlock = assertQuoteBlock(result[0]);
    assert.lengthOf(quoteBlock.blocks, 1);
    assertSimpleTextBlock(quoteBlock.blocks[0], 'Quote text');
  });

  test('parse quote lead space', () => {
    const comment = ' > Quote text';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    const quoteBlock = assertQuoteBlock(result[0]);
    assert.lengthOf(quoteBlock.blocks, 1);
    assertSimpleTextBlock(quoteBlock.blocks[0], 'Quote text');
  });

  test('parse multiline quote', () => {
    const comment = '> Quote line 1\n> Quote line 2\n > Quote line 3\n';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    const quoteBlock = assertQuoteBlock(result[0]);
    assert.lengthOf(quoteBlock.blocks, 1);
    assertSimpleTextBlock(
      quoteBlock.blocks[0],
      'Quote line 1\nQuote line 2\nQuote line 3'
    );
  });

  test('parse pre', () => {
    const comment = '    Four space indent.';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertPreBlock(result[0], comment);
  });

  test('parse one space pre', () => {
    const comment = ' One space indent.\n Another line.';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertPreBlock(result[0], comment);
  });

  test('parse tab pre', () => {
    const comment = '\tOne tab indent.\n\tAnother line.\n  Yet another!';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertPreBlock(result[0], comment);
  });

  test('parse star list', () => {
    const comment = '* Item 1\n* Item 2\n* Item 3';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertListBlock(result[0], [
      {spans: [{type: 'text', text: 'Item 1'}]},
      {spans: [{type: 'text', text: 'Item 2'}]},
      {spans: [{type: 'text', text: 'Item 3'}]},
    ]);
  });

  test('parse dash list', () => {
    const comment = '- Item 1\n- Item 2\n- Item 3';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertListBlock(result[0], [
      {spans: [{type: 'text', text: 'Item 1'}]},
      {spans: [{type: 'text', text: 'Item 2'}]},
      {spans: [{type: 'text', text: 'Item 3'}]},
    ]);
  });

  test('parse mixed list', () => {
    const comment = '- Item 1\n* Item 2\n- Item 3\n* Item 4';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertListBlock(result[0], [
      {spans: [{type: 'text', text: 'Item 1'}]},
      {spans: [{type: 'text', text: 'Item 2'}]},
      {spans: [{type: 'text', text: 'Item 3'}]},
      {spans: [{type: 'text', text: 'Item 4'}]},
    ]);
  });

  test('parse mixed block types', () => {
    const comment =
      'Paragraph\nacross\na\nfew\nlines.' +
      '\n\n' +
      '> Quote\n> across\n> not many lines.' +
      '\n\n' +
      'Another paragraph' +
      '\n\n' +
      '* Series\n* of\n* list\n* items' +
      '\n\n' +
      'Yet another paragraph' +
      '\n\n' +
      '\tPreformatted text.' +
      '\n\n' +
      'Parting words.';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 7);
    assertSimpleTextBlock(result[0], 'Paragraph\nacross\na\nfew\nlines.\n');

    const quoteBlock = assertQuoteBlock(result[1]);
    assert.lengthOf(quoteBlock.blocks, 1);
    assertSimpleTextBlock(
      quoteBlock.blocks[0],
      'Quote\nacross\nnot many lines.'
    );

    assertSimpleTextBlock(result[2], 'Another paragraph\n');
    assertListBlock(result[3], [
      {spans: [{type: 'text', text: 'Series'}]},
      {spans: [{type: 'text', text: 'of'}]},
      {spans: [{type: 'text', text: 'list'}]},
      {spans: [{type: 'text', text: 'items'}]},
    ]);
    assertSimpleTextBlock(result[4], 'Yet another paragraph\n');
    assertPreBlock(result[5], '\tPreformatted text.');
    assertSimpleTextBlock(result[6], 'Parting words.');
  });

  test('bullet list 1', () => {
    const comment = 'A\n\n* line 1';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertSimpleTextBlock(result[0], 'A\n');
    assertListBlock(result[1], [{spans: [{type: 'text', text: 'line 1'}]}]);
  });

  test('bullet list 2', () => {
    const comment = 'A\n\n* line 1\n* 2nd line';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertSimpleTextBlock(result[0], 'A\n');
    assertListBlock(result[1], [
      {spans: [{type: 'text', text: 'line 1'}]},
      {spans: [{type: 'text', text: '2nd line'}]},
    ]);
  });

  test('bullet list 3', () => {
    const comment = 'A\n* line 1\n* 2nd line\n\nB';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 3);
    assertSimpleTextBlock(result[0], 'A');
    assertListBlock(result[1], [
      {spans: [{type: 'text', text: 'line 1'}]},
      {spans: [{type: 'text', text: '2nd line'}]},
    ]);
    assertSimpleTextBlock(result[2], 'B');
  });

  test('bullet list 4', () => {
    const comment = '* line 1\n* 2nd line\n\nB';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertListBlock(result[0], [
      {spans: [{type: 'text', text: 'line 1'}]},
      {spans: [{type: 'text', text: '2nd line'}]},
    ]);
    assertSimpleTextBlock(result[1], 'B');
  });

  test('bullet list 5', () => {
    const comment =
      'To see this bug, you have to:\n' +
      '* Be on IMAP or EAS (not on POP)\n' +
      '* Be very unlucky\n';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertSimpleTextBlock(result[0], 'To see this bug, you have to:');
    assertListBlock(result[1], [
      {spans: [{type: 'text', text: 'Be on IMAP or EAS (not on POP)'}]},
      {spans: [{type: 'text', text: 'Be very unlucky'}]},
    ]);
  });

  test('bullet list 6', () => {
    const comment =
      'To see this bug,\n' +
      'you have to:\n' +
      '* Be on IMAP or EAS (not on POP)\n' +
      '* Be very unlucky\n';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertSimpleTextBlock(result[0], 'To see this bug,\nyou have to:');
    assertListBlock(result[1], [
      {spans: [{type: 'text', text: 'Be on IMAP or EAS (not on POP)'}]},
      {spans: [{type: 'text', text: 'Be very unlucky'}]},
    ]);
  });

  test('dash list 1', () => {
    const comment = 'A\n- line 1\n- 2nd line';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertSimpleTextBlock(result[0], 'A');
    assertListBlock(result[1], [
      {spans: [{type: 'text', text: 'line 1'}]},
      {spans: [{type: 'text', text: '2nd line'}]},
    ]);
  });

  test('dash list 2', () => {
    const comment = 'A\n- line 1\n- 2nd line\n\nB';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 3);
    assertSimpleTextBlock(result[0], 'A');
    assertListBlock(result[1], [
      {spans: [{type: 'text', text: 'line 1'}]},
      {spans: [{type: 'text', text: '2nd line'}]},
    ]);
    assertSimpleTextBlock(result[2], 'B');
  });

  test('dash list 3', () => {
    const comment = '- line 1\n- 2nd line\n\nB';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertListBlock(result[0], [
      {spans: [{type: 'text', text: 'line 1'}]},
      {spans: [{type: 'text', text: '2nd line'}]},
    ]);
    assertSimpleTextBlock(result[1], 'B');
  });

  test('list with links', () => {
    const comment = '- [text](http://url)\n- 2nd line';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertListBlock(result[0], [
      {
        spans: [{type: 'link', text: 'text', url: 'http://url'}],
      },
      {spans: [{type: 'text', text: '2nd line'}]},
    ]);
  });

  test('nested list will NOT be recognized', () => {
    // will be rendered as two separate lists
    const comment = '- line 1\n  - line with indentation\n- line 2';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 3);
    assertListBlock(result[0], [{spans: [{type: 'text', text: 'line 1'}]}]);
    assertPreBlock(result[1], '  - line with indentation');
    assertListBlock(result[2], [{spans: [{type: 'text', text: 'line 2'}]}]);
  });

  test('pre format 1', () => {
    const comment = 'A\n  This is pre\n  formatted';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertSimpleTextBlock(result[0], 'A');
    assertPreBlock(result[1], '  This is pre\n  formatted');
  });

  test('pre format 2', () => {
    const comment = 'A\n  This is pre\n  formatted\n\nbut this is not';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 3);
    assertSimpleTextBlock(result[0], 'A');
    assertPreBlock(result[1], '  This is pre\n  formatted');
    assertSimpleTextBlock(result[2], 'but this is not');
  });

  test('pre format 3', () => {
    const comment = 'A\n  Q\n    <R>\n  S\n\nB';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 3);
    assertSimpleTextBlock(result[0], 'A');
    assertPreBlock(result[1], '  Q\n    <R>\n  S');
    assertSimpleTextBlock(result[2], 'B');
  });

  test('pre format 4', () => {
    const comment = '  Q\n    <R>\n  S\n\nB';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertPreBlock(result[0], '  Q\n    <R>\n  S');
    assertSimpleTextBlock(result[1], 'B');
  });

  test('pre format 5', () => {
    const comment = '  Q\n    <R>\n  S\n \nB';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertPreBlock(result[0], '  Q\n    <R>\n  S');
    assertSimpleTextBlock(result[1], ' \nB');
  });

  test('pre format 6', () => {
    const comment = '  Q\n    <R>\n\n  S\n \nB';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertPreBlock(result[0], '  Q\n    <R>\n\n  S');
    assertSimpleTextBlock(result[1], ' \nB');
  });

  test('quote 1', () => {
    const comment = "> I'm happy with quotes!!";
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    const quoteBlock = assertQuoteBlock(result[0]);
    assert.lengthOf(quoteBlock.blocks, 1);
    assertSimpleTextBlock(quoteBlock.blocks[0], "I'm happy with quotes!!");
  });

  test('quote 2', () => {
    const comment = "> I'm happy\n > with quotes!\n\nSee above.";
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    const quoteBlock = assertQuoteBlock(result[0]);
    assert.lengthOf(quoteBlock.blocks, 1);
    assertSimpleTextBlock(quoteBlock.blocks[0], "I'm happy\nwith quotes!");
    assertSimpleTextBlock(result[1], 'See above.');
  });

  test('quote 3', () => {
    const comment = 'See this said:\n > a quoted\n > string block\n\nOK?';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 3);
    assertSimpleTextBlock(result[0], 'See this said:');
    const quoteBlock = assertQuoteBlock(result[1]);
    assert.lengthOf(quoteBlock.blocks, 1);
    assertSimpleTextBlock(quoteBlock.blocks[0], 'a quoted\nstring block');
    assertSimpleTextBlock(result[2], 'OK?');
  });

  test('nested quotes', () => {
    const comment = ' > > prior\n > \n > next\n';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    const outerQuoteBlock = assertQuoteBlock(result[0]);
    assert.lengthOf(outerQuoteBlock.blocks, 2);
    const nestedQuoteBlock = assertQuoteBlock(outerQuoteBlock.blocks[0]);
    assert.lengthOf(nestedQuoteBlock.blocks, 1);
    assertSimpleTextBlock(nestedQuoteBlock.blocks[0], 'prior');
    assertSimpleTextBlock(outerQuoteBlock.blocks[1], 'next');
  });

  test('code 1', () => {
    const comment = '```\n// test code\n```';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertCodeBlock(result[0], '// test code');
  });

  test('code 2', () => {
    const comment = 'test code\n```// test code```';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertSimpleTextBlock(result[0], 'test code');
    assertCodeBlock(result[1], '// test code');
  });

  test('not a code block', () => {
    const comment = 'test code\n```// test code';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 1);
    assertSimpleTextBlock(result[0], 'test code\n```// test code');
  });

  test('not a code block 2', () => {
    const comment = 'test code\n```\n// test code';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertSimpleTextBlock(result[0], 'test code');
    assertSimpleTextBlock(result[1], '```\n// test code');
  });

  test('not a code block 3', () => {
    const comment = 'test code\n```';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 2);
    assertSimpleTextBlock(result[0], 'test code');
    assertSimpleTextBlock(result[1], '```');
  });

  test('mix all 1', () => {
    const comment =
      ' bullets:\n- bullet 1\n- bullet 2\n\ncode example:\n' +
      '```// test code```\n\n> reference is here';
    const result = element._computeBlocks(comment);
    assert.lengthOf(result, 5);
    assert.equal(result[0].type, 'pre');
    assert.equal(result[1].type, 'list');
    assert.equal(result[2].type, 'paragraph');
    assert.equal(result[3].type, 'code');
    assert.equal(result[4].type, 'quote');
  });
});
