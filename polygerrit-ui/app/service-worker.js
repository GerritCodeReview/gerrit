/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
importScripts('https://storage.googleapis.com/workbox-cdn/releases/5.1.2/workbox-sw.js');

if (workbox) {
  console.log(`Yay! Workbox is loaded 🎉`);
} else {
  console.log(`Boo! Workbox didn't load 😬`);
}

workbox.loadModule('workbox-strategies');

console.log(`${self}`);

self.addEventListener('install', (event) => {
  console.log(`installed`);
  const urls = [
    "https://cdn.googlesource.com/polygerrit_ui/867.0/bower_components/highlightjs/highlight.min.js",

  ];
  const cacheName = workbox.core.cacheNames.runtime;
  event.waitUntil(caches.open(cacheName).then((cache) => cache.addAll(urls)));
});

self.addEventListener('fetch', (event) => {
  const {request} = event;
  const url = new URL(request.url);
  if (request.url.includes('/elements/') || request.url.includes('node_modules/'))
    return;
  console.log(`fetch ${request.url} ${url.origin} ${url.pathname}`);
  if (request.url.includes('/diff?')
  || request.url.includes('highlight.min.js') 
  || request.url.includes('detail?O=916314')
  || request.url.includes('comments')) {
    console.log(`load from cache`);
    // Using the previously-initialized strategies will work as expected.
    const cacheFirst = new workbox.strategies.CacheFirst();
    event.respondWith(cacheFirst.handle({request: event.request}));
  }
  // if (url.origin === location.origin && url.pathname === '/') {
    // event.respondWith(new workbox.strategies.StaleWhileRevalidate().handle({event, request}));
  // }
});

self.addEventListener("message", function(e) {
  const {data} = e;
  const cacheName = workbox.core.cacheNames.runtime;
  if (data.type === 'Files') {
    const {files, changeId, project, revision} = data;
    const urls = files.map(file => {
      const req =  `http://localhost:8081/changes/${project}~${changeId}/revisions/${revision}/files/${file.replaceAll('/', '%2F')}/diff?intraline&whitespace=IGNORE_NONE`
      console.log(`req files: ${req}`);
      return req;
    });
    caches.open(cacheName).then((cache) => cache.addAll(urls));
  } else if (data.type === 'Changes') {
    const {changes} = data;
    const urls = changes.map(change => {
      const details =  `http://localhost:8081/changes/${change.project}~${change.changeId}/detail?O=916314`;
      const comments = `http://localhost:8081/changes/${change.project}~${change.changeId}/comments?enable-context=true&context-padding=3`;
      const robotcomments = `http://localhost:8081/changes/${change.project}~${change.changeId}/robotcomments`;
      const portedcomments = `http://localhost:8081/changes/${change.project}~${change.changeId}/revisions/current/ported_comments/`;
      return [details, comments, robotcomments, portedcomments];
    });
    caches.open(cacheName).then((cache) => cache.addAll(urls.flat()));
  } else {
    console.log(`data: ${data}`);
  }

}, false);