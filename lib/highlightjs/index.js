var hljs = require('highlight.js');

hljs.registerLanguage('soy', require('highlightjs-closure-templates'));

module.exports = hljs;
