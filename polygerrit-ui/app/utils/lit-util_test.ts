/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import '../test/common-test-setup';
import {HtmlPatched} from './lit-util';

function tsa(strings: string[]): TemplateStringsArray {
  return strings as unknown as TemplateStringsArray;
}

suite('lit-util HtmlPatched tests', () => {
  let patched: HtmlPatched;
  let nativeHtmlSpy: sinon.SinonSpy;
  let reporterSpy: sinon.SinonSpy;

  setup(async () => {
    reporterSpy = sinon.spy();
    patched = new HtmlPatched(reporterSpy);
    nativeHtmlSpy = sinon.spy(patched, 'nativeHtml');
  });

  test('simple call', () => {
    const instance1 = tsa(['1']);
    patched.html(instance1, 'a value');
    assert.equal(nativeHtmlSpy.callCount, 1);
    assert.equal(reporterSpy.callCount, 0);
    assert.strictEqual(nativeHtmlSpy.getCalls()[0].args[0], instance1);
    assert.strictEqual(nativeHtmlSpy.getCalls()[0].args[1], 'a value');
  });

  test('two calls, same instance', () => {
    const instance1 = tsa(['1']);
    patched.html(instance1, 'a value');
    patched.html(instance1, 'a value');
    assert.equal(nativeHtmlSpy.callCount, 2);
    assert.equal(reporterSpy.callCount, 0);
    assert.strictEqual(nativeHtmlSpy.getCalls()[0].firstArg, instance1);
    assert.strictEqual(nativeHtmlSpy.getCalls()[1].firstArg, instance1);
  });

  test('two calls, different strings', () => {
    const instance1 = tsa(['1']);
    const instance2 = tsa(['2']);
    patched.html(instance1, 'a value');
    patched.html(instance2, 'a value');
    assert.equal(nativeHtmlSpy.callCount, 2);
    assert.equal(reporterSpy.callCount, 0);
    assert.strictEqual(nativeHtmlSpy.getCalls()[0].firstArg, instance1);
    assert.strictEqual(nativeHtmlSpy.getCalls()[1].firstArg, instance2);
  });

  test('two calls, same strings, different instances', () => {
    const instance1 = tsa(['1']);
    const instance2 = tsa(['1']);
    patched.html(instance1, 'a value');
    patched.html(instance2, 'a value');
    assert.equal(nativeHtmlSpy.callCount, 2);
    assert.equal(reporterSpy.callCount, 1);
    assert.strictEqual(nativeHtmlSpy.getCalls()[0].firstArg, instance1);
    assert.strictEqual(nativeHtmlSpy.getCalls()[1].firstArg, instance1);
  });

  test('many calls', () => {
    const instance1a = tsa(['1']);
    const instance1b = tsa(['1']);
    const instance1c = tsa(['1']);
    const instance2a = tsa(['asdf', 'qwer']);
    const instance2b = tsa(['asdf', 'qwer']);
    const instance2c = tsa(['asdf', 'qwer']);
    const instance3a = tsa(['asd', 'fqwer']);
    const instance3b = tsa(['asd', 'fqwer']);
    const instance3c = tsa(['asd', 'fqwer']);

    patched.html(instance1a, 'a value');
    patched.html(instance1a, 'a value');
    patched.html(instance1b, 'a value');
    patched.html(instance1b, 'a value');
    patched.html(instance1c, 'a value');
    patched.html(instance1c, 'a value');
    patched.html(instance2a, 'a value');
    patched.html(instance2a, 'a value');
    patched.html(instance2b, 'a value');
    patched.html(instance2b, 'a value');
    patched.html(instance2c, 'a value');
    patched.html(instance2c, 'a value');
    patched.html(instance3a, 'a value');
    patched.html(instance3a, 'a value');
    patched.html(instance3b, 'a value');
    patched.html(instance3b, 'a value');
    patched.html(instance3c, 'a value');
    patched.html(instance3c, 'a value');

    assert.equal(nativeHtmlSpy.callCount, 18);
    assert.equal(reporterSpy.callCount, 12);

    assert.strictEqual(nativeHtmlSpy.getCalls()[0].firstArg, instance1a);
    assert.strictEqual(nativeHtmlSpy.getCalls()[1].firstArg, instance1a);
    assert.strictEqual(nativeHtmlSpy.getCalls()[2].firstArg, instance1a);
    assert.strictEqual(nativeHtmlSpy.getCalls()[3].firstArg, instance1a);
    assert.strictEqual(nativeHtmlSpy.getCalls()[4].firstArg, instance1a);
    assert.strictEqual(nativeHtmlSpy.getCalls()[5].firstArg, instance1a);
    assert.strictEqual(nativeHtmlSpy.getCalls()[6].firstArg, instance2a);
    assert.strictEqual(nativeHtmlSpy.getCalls()[7].firstArg, instance2a);
    assert.strictEqual(nativeHtmlSpy.getCalls()[8].firstArg, instance2a);
    assert.strictEqual(nativeHtmlSpy.getCalls()[9].firstArg, instance2a);
    assert.strictEqual(nativeHtmlSpy.getCalls()[10].firstArg, instance2a);
    assert.strictEqual(nativeHtmlSpy.getCalls()[11].firstArg, instance2a);
    assert.strictEqual(nativeHtmlSpy.getCalls()[12].firstArg, instance3a);
    assert.strictEqual(nativeHtmlSpy.getCalls()[13].firstArg, instance3a);
    assert.strictEqual(nativeHtmlSpy.getCalls()[14].firstArg, instance3a);
    assert.strictEqual(nativeHtmlSpy.getCalls()[15].firstArg, instance3a);
    assert.strictEqual(nativeHtmlSpy.getCalls()[16].firstArg, instance3a);
    assert.strictEqual(nativeHtmlSpy.getCalls()[17].firstArg, instance3a);
  });
});
