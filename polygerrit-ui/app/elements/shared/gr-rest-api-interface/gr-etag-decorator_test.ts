/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import {GrEtagDecorator} from './gr-etag-decorator';

suite('gr-etag-decorator', () => {
  let etag: GrEtagDecorator;

  const fakeRequest = (opt_etag?: string, opt_status?: number) => {
    const headers = new Headers();
    if (opt_etag) {
      headers.set('etag', opt_etag);
    }
    const status = opt_status || 200;
    return {...new Response(), ok: true, status, headers};
  };

  setup(() => {
    etag = new GrEtagDecorator();
  });

  test('exists', () => {
    assert.isOk(etag);
  });

  test('works', () => {
    etag.collect('/foo', fakeRequest('bar'), '');
    const options = etag.getOptions('/foo');
    assert.strictEqual(options!.headers!.get('If-None-Match'), 'bar');
  });

  test('updates etags', () => {
    etag.collect('/foo', fakeRequest('bar'), '');
    etag.collect('/foo', fakeRequest('baz'), '');
    const options = etag.getOptions('/foo');
    assert.strictEqual(options!.headers!.get('If-None-Match'), 'baz');
  });

  test('discards empty etags', () => {
    etag.collect('/foo', fakeRequest('bar'), '');
    etag.collect('/foo', fakeRequest(), '');
    const options = etag.getOptions('/foo', {headers: new Headers()});
    assert.isNull(options!.headers!.get('If-None-Match'));
  });

  test('discards etags in order used', () => {
    etag.collect('/foo', fakeRequest('bar'), '');
    for (let i = 0; i < 29; i++) {
      etag.collect(`/qaz/${i}`, fakeRequest('qaz'), '');
    }
    let options = etag.getOptions('/foo');
    assert.strictEqual(options!.headers!.get('If-None-Match'), 'bar');
    etag.collect('/zaq', fakeRequest('zaq'), '');
    options = etag.getOptions('/foo', {headers: new Headers()});
    assert.isNull(options!.headers!.get('If-None-Match'));
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
