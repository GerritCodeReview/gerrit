/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import 'lodash/lodash.js';
import {GrEtagDecorator} from './gr-etag-decorator.js';

suite('gr-etag-decorator', () => {
  let etag;

  const fakeRequest = (opt_etag, opt_status) => {
    const headers = new Headers();
    if (opt_etag) {
      headers.set('etag', opt_etag);
    }
    const status = opt_status || 200;
    return {ok: true, status, headers};
  };

  setup(() => {
    etag = new GrEtagDecorator();
  });

  test('exists', () => {
    assert.isOk(etag);
  });

  test('works', () => {
    etag.collect('/foo', fakeRequest('bar'));
    const options = etag.getOptions('/foo');
    assert.strictEqual(options.headers.get('If-None-Match'), 'bar');
  });

  test('updates etags', () => {
    etag.collect('/foo', fakeRequest('bar'));
    etag.collect('/foo', fakeRequest('baz'));
    const options = etag.getOptions('/foo');
    assert.strictEqual(options.headers.get('If-None-Match'), 'baz');
  });

  test('discards empty etags', () => {
    etag.collect('/foo', fakeRequest('bar'));
    etag.collect('/foo', fakeRequest());
    const options = etag.getOptions('/foo', {headers: new Headers()});
    assert.isNull(options.headers.get('If-None-Match'));
  });

  test('discards etags in order used', () => {
    etag.collect('/foo', fakeRequest('bar'));
    _.times(29, i => {
      etag.collect('/qaz/' + i, fakeRequest('qaz'));
    });
    let options = etag.getOptions('/foo');
    assert.strictEqual(options.headers.get('If-None-Match'), 'bar');
    etag.collect('/zaq', fakeRequest('zaq'));
    options = etag.getOptions('/foo', {headers: new Headers()});
    assert.isNull(options.headers.get('If-None-Match'));
  });

  test('getCachedPayload', () => {
    const payload = 'payload';
    etag.collect('/foo', fakeRequest('bar'), payload);
    assert.strictEqual(etag.getCachedPayload('/foo'), payload);
    etag.collect('/foo', fakeRequest('bar', 304), 'garbage');
    assert.strictEqual(etag.getCachedPayload('/foo'), payload);
    etag.collect('/foo', fakeRequest('bar', 200), 'new payload');
    assert.strictEqual(etag.getCachedPayload('/foo'), 'new payload');
  });
});

