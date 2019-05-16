/**
 * @fileoverview Setup that needs to run before HTML imports are available.
 */
window.POLYMER2 = true;
if (window.customElements) window.customElements.forcePolyfill = true;
ShadyDOM = {force: true};
ShadyCSS = {shimcssproperties: true};

