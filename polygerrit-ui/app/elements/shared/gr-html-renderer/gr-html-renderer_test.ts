import '../../../test/common-test-setup';
import './gr-html-renderer';
import {GrHtmlRenderer} from './gr-html-renderer';
import {fixture, html} from '@open-wc/testing-helpers';
import {assert} from '@open-wc/testing';

suite('gr-html-renderer tests', () => {
  let element: GrHtmlRenderer;

  setup(async () => {
    element = await fixture<GrHtmlRenderer>(
      html`<gr-html-renderer></gr-html-renderer>`
    );
  });

  test('renders html content', async () => {
    const content = '<b>bold</b>';
    element.htmlContent = content;
    await element.updateComplete;
    assert.lightDom.equal(element, content);
  });

  test('renders empty string for undefined content', async () => {
    element.htmlContent = undefined;
    await element.updateComplete;
    assert.lightDom.equal(element, '');
  });
});
