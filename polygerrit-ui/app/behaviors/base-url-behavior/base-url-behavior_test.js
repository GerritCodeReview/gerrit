import '../../test/common-test-setup-karma.js';
import {Polymer} from '@polymer/polymer/lib/legacy/polymer-fn.js';
import {BaseUrlBehavior} from './base-url-behavior.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

/** @type {string} */
window.CANONICAL_PATH = '/r';

const basicFixture = fixtureFromElement('test-element');
const withinOverlayFixture = fixtureFromTemplate(html`
  <gr-overlay>
    <test-element></test-element>
  </gr-overlay>
`);

suite('base-url-behavior tests', () => {
  let element;
  // eslint-disable-next-line no-unused-vars
  let overlay;

  suiteSetup(() => {
    // Define a Polymer element that uses this behavior.
    Polymer({
      is: 'test-element',
      behaviors: [
        BaseUrlBehavior,
      ],
    });
  });

  setup(() => {
    element = basicFixture.instantiate();
    overlay = withinOverlayFixture.instantiate();
  });

  test('getBaseUrl', () => {
    assert.deepEqual(element.getBaseUrl(), '/r');
  });
});
