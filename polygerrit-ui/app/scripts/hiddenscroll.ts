/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

let hiddenscroll: boolean | undefined = undefined;

window.addEventListener('WebComponentsReady', () => {
  const elem = document.createElement('div');
  elem.setAttribute('style', 'width:100px;height:100px;overflow:scroll');
  document.body.appendChild(elem);
  hiddenscroll = elem.offsetWidth === elem.clientWidth;
  elem.remove();
});

export function _setHiddenScroll(value: boolean) {
  hiddenscroll = value;
}

export function getHiddenScroll() {
  return hiddenscroll;
}
