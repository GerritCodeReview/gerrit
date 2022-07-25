/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {readResponsePayload} from '../elements/shared/gr-rest-api-interface/gr-rest-apis/gr-rest-api-helper';
import {ParsedChangeInfo} from '../types/types';
/* eslint-disable no-console*/

console.log('Hello World!');

function getGlobalScope() {
  return self as {} as ServiceWorkerGlobalScope;
}

self.addEventListener('push', async () => {
  console.log('push');
  //   const response = await fetch('http://localhost:8080/q/attention:self');
  const response = await fetch(
    'http://localhost:8080/changes/?O=1000081&S=0&n=25&q=status%3Aopen%20-is%3Awip'
  );
  console.log(response);
  const payload = await readResponsePayload(response);
  const changes = (await payload.parsed) as unknown as ParsedChangeInfo[];
  console.log(changes);
  if (changes.length > 0) {
    console.log('Changes!');
    const options = {};
    getGlobalScope().registration.showNotification(changes[0].subject, options);
  }
});
