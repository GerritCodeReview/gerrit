/**
 * After M80, htmlImports has been removed and the polyfill from
 * webcomponents can only support async html imports unlike
 * native htmlImports which loads htmls synchronously.
 */
window.readyToTest = () => Promise.race([
  new Promise(resolve =>
    window.addEventListener('HTMLImportsLoaded', resolve)),
  // timeout after 5s, the test timeout is 10s
  new Promise(resolve => setTimeout(resolve, 5000)),
]);
