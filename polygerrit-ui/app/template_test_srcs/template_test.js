const fs = require('fs');
const twinkie = require('fried-twinkie');

/**
 * For the purposes of template type checking, externs should be added for
 * anything set on the window object. Note that sub-properties of these
 * declared properties are considered something separate.
 *
 * @todo (beckysiegel) Gerrit's class definitions should be recognized in
 *    closure types.
 */
const EXTERN_NAMES = [
  'Gerrit',
  'GrAnnotation',
  'GrAttributeHelper',
  'GrChangeActionsInterface',
  'GrChangeReplyInterface',
  'GrDiffBuilder',
  'GrDiffBuilderImage',
  'GrDiffBuilderSideBySide',
  'GrDiffBuilderUnified',
  'GrDiffGroup',
  'GrDiffLine',
  'GrDomHooks',
  'GrEtagDecorator',
  'GrGapiAuth',
  'GrGerritAuth',
  'GrLinkTextParser',
  'GrPluginEndpoints',
  'GrPopupInterface',
  'GrRangeNormalizer',
  'GrReporting',
  'GrReviewerUpdatesParser',
  'GrThemeApi',
  'moment',
  'page',
  'util',
];

fs.readdir('./polygerrit-ui/temp/behaviors/', (err, data) => {
  if (err) {
    console.log('error /polygerrit-ui/temp/behaviors/ directory');
  }
  const behaviors = data;
  const externs = [];

  for (const behavior of behaviors) {
    externs.push({
      path: `./polygerrit-ui/temp/behaviors/${behavior}`,
      src: fs.readFileSync(
          `./polygerrit-ui/temp/behaviors/${behavior}`, 'utf-8'),
    });
  }

  let mappings = JSON.parse(fs.readFileSync(
      `./polygerrit-ui/temp/map.json`, 'utf-8'));

  // The directory is passed as arg2 by the test target.
  const directory = process.argv[2];
  if (directory) {
    const mappingSpecificDirectory = {};

    for (key of Object.keys(mappings)) {
      if (directory === mappings[key].directory) {
        mappingSpecificDirectory[key] = mappings[key];
      }
    }
    mappings = mappingSpecificDirectory;
  }

  // If a particular file was passed by the user, don't test everything.
  const file = process.argv[3];
  if (file) {
    const mappingSpecificFile = {};
    for (key of Object.keys(mappings)) {
      if (key.includes(file)) {
        mappingSpecificFile[key] = mappings[key];
      }
    }
    mappings = mappingSpecificFile;
  }

  externs.push({
    path: 'custom-externs.js',
    src: '/** @externs */' +
        EXTERN_NAMES.map( name => { return `var ${name};`; }).join(' '),
  });

  const promises = [];

  for (key of Object.keys(mappings)) {
    if (mappings[key].html && mappings[key].js) {
      promises.push(twinkie.checkTemplate(
          mappings[key].html,
          mappings[key].js,
          'polygerrit.' + mappings[key].package,
          externs
      ));
    }
  }

  Promise.all(promises).then(() => {}, joinedErrors => {
    if (joinedErrors) {
      process.exit(1);
    }
  });
});
