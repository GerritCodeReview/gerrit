var hljs = require('highlight.js');

hljs.registerLanguage('soy', require('highlightjs-closure-templates'));
hljs.registerLanguage('iecst', require('highlightjs-structured-text'));

module.exports = hljs;
