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

import '../../../test/common-test-setup-karma.js';
import './gr-smart-search.js';

const basicFixture = fixtureFromElement('gr-smart-search');

suite('gr-smart-search tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('Autocompletes accounts', () => {
    sinon.stub(element.$.restAPI, 'getSuggestedAccounts').callsFake(() => {
      return Promise.resolve([
        {
          name: 'fred',
          email: 'fred@goog.co',
        },
      ]);
    }
    );
    return element._fetchAccounts('owner', 'fr').then(s => {
      assert.deepEqual(s[0], {text: 'owner:fred@goog.co', label: 'fred'});
    });
  });

  test('Inserts self as option when valid', () => {
    sinon.stub(element.$.restAPI, 'getSuggestedAccounts').callsFake( () => {
      return Promise.resolve([
        {
          name: 'fred',
          email: 'fred@goog.co',
        },
      ]);
    }
    );
    element._fetchAccounts('owner', 's')
        .then(s => {
          assert.deepEqual(s[0], {text: 'owner:fred@goog.co', label: 'fred'});
          assert.deepEqual(s[1], {text: 'owner:self'});
        })
        .then(() => { return element._fetchAccounts('owner', 'selfs'); })
        .then(s => {
          assert.notEqual(s[0], {text: 'owner:self'});
        });
  });

  test('Inserts me as option when valid', () => {
    sinon.stub(element.$.restAPI, 'getSuggestedAccounts').callsFake( () => {
      return Promise.resolve([
        {
          name: 'fred',
          email: 'fred@goog.co',
        },
      ]);
    }
    );
    return element._fetchAccounts('owner', 'm')
        .then(s => {
          assert.deepEqual(s[0], {text: 'owner:fred@goog.co', label: 'fred'});
          assert.deepEqual(s[1], {text: 'owner:me'});
        })
        .then(() => { return element._fetchAccounts('owner', 'meme'); })
        .then(s => {
          assert.notEqual(s[0], {text: 'owner:me'});
        });
  });

  test('Autocompletes groups', () => {
    sinon.stub(element.$.restAPI, 'getSuggestedGroups').callsFake( () => {
      return Promise.resolve({
        Polygerrit: 0,
        gerrit: 0,
        gerrittest: 0,
      });
    }
    );
    return element._fetchGroups('ownerin', 'pol').then(s => {
      assert.deepEqual(s[0], {text: 'ownerin:Polygerrit'});
    });
  });

  test('Autocompletes projects', () => {
    sinon.stub(element.$.restAPI, 'getSuggestedProjects').callsFake( () => { return Promise.resolve({Polygerrit: 0}); });
    return element._fetchProjects('project', 'pol').then(s => {
      assert.deepEqual(s[0], {text: 'project:Polygerrit'});
    });
  });

  test('Autocomplete doesnt override exact matches to input', () => {
    sinon.stub(element.$.restAPI, 'getSuggestedGroups').callsFake( () => {
      return Promise.resolve({
        Polygerrit: 0,
        gerrit: 0,
        gerrittest: 0,
      });
    }
    );
    return element._fetchGroups('ownerin', 'gerrit').then(s => {
      assert.deepEqual(s[0], {text: 'ownerin:Polygerrit'});
      assert.deepEqual(s[1], {text: 'ownerin:gerrit'});
      assert.deepEqual(s[2], {text: 'ownerin:gerrittest'});
    });
  });

  test('Autocompletes accounts with no email', () => {
    sinon.stub(element.$.restAPI, 'getSuggestedAccounts').callsFake( () => { return Promise.resolve([{name: 'fred'}]); });
    return element._fetchAccounts('owner', 'fr').then(s => {
      assert.deepEqual(s[0], {text: 'owner:"fred"', label: 'fred'});
    });
  });

  test('Autocompletes accounts with email', () => {
    sinon.stub(element.$.restAPI, 'getSuggestedAccounts').callsFake( () => { return Promise.resolve([{email: 'fred@goog.co'}]); });
    return element._fetchAccounts('owner', 'fr').then(s => {
      assert.deepEqual(s[0], {text: 'owner:fred@goog.co', label: ''});
    });
  });
});

