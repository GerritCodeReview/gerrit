var path = require('path');

var ret = {
  suites: ['app/test'],
  webserver: {
    pathMappings: []
  },
  testTimeout: 500000
};

var mapping = {};
var rootPath = (__dirname).split(path.sep).slice(-1)[0];

mapping['/components/' + rootPath  + '/app/bower_components'] = 'bower_components';

ret.webserver.pathMappings.push(mapping);

module.exports = ret;
