const fs = require('fs');
const crisper = require('crisper');
const glob = require('glob');

const outPutFile = process.argv[2];
const allFiles = process.argv.slice(3);
const tempDir = './polygerrit-ui/.temp/crisper';

fs.mkdirSync(tempDir, {recursive: true});

// copy all js files to above temp dir
allFiles.filter(file => file.endsWith('.js')).forEach(fileName => {
  // copy js file
  fs.copyFileSync(
      fileName, tempDir + '/' + fileName.split('/').pop(),
      fs.constants.COPYFILE_EXCL);
});

// crisper all js from all html files
allFiles.filter(file => file.endsWith('.html')).forEach(fileName => {
  const output = crisper({
    source: fs.readFileSync(fileName, 'utf-8'),
  });
  const outputJsFile = fileName.split('/').pop() + '.js';
  fs.writeFileSync(tempDir + '/' + outputJsFile, output.js, 'utf-8');
});

// concat all js files
glob(tempDir + '/**/*.js', {}, (err, files) => {
  let allCode = '';
  files.forEach(fileName => {
    allCode += fs.readFileSync(fileName, 'utf-8');
  });

  fs.writeFileSync(outPutFile, allCode, 'utf-8');
});
