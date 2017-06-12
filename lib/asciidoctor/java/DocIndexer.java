// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.server.documentation.Constants;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.RAMDirectory;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class DocIndexer {
  private static final Pattern SECTION_HEADER = Pattern.compile("^=+ (.*)");

  @Option(name = "-o", usage = "output JAR file")
  private String outFile;

  @Option(name = "--prefix", usage = "prefix for the html filepath")
  private String prefix = "";

  @Option(name = "--in-ext", usage = "extension for input files")
  private String inExt = ".txt";

  @Option(name = "--out-ext", usage = "extension for output files")
  private String outExt = ".html";

  @Argument(usage = "input files")
  private List<String> inputFiles = new ArrayList<>();

  private void invoke(String... parameters) throws IOException {
    CmdLineParser parser = new CmdLineParser(this);
    try {
      parser.parseArgument(parameters);
      if (inputFiles.isEmpty()) {
        throw new CmdLineException(parser, "FAILED: input file missing");
      }
    } catch (CmdLineException e) {
      System.err.println(e.getMessage());
      parser.printUsage(System.err);
      System.exit(1);
      return;
    }

    try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(outFile))) {
      byte[] compressedIndex = zip(index());
      JarEntry entry = new JarEntry(String.format("%s/%s", Constants.PACKAGE, Constants.INDEX_ZIP));
      entry.setSize(compressedIndex.length);
      jar.putNextEntry(entry);
      jar.write(compressedIndex);
      jar.closeEntry();
    }
  }

  private RAMDirectory index()
      throws IOException, UnsupportedEncodingException, FileNotFoundException {
    RAMDirectory directory = new RAMDirectory();
    IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer(CharArraySet.EMPTY_SET));
    config.setOpenMode(OpenMode.CREATE);
    config.setCommitOnClose(true);
    try (IndexWriter iwriter = new IndexWriter(directory, config)) {
      for (String inputFile : inputFiles) {
        File file = new File(inputFile);
        if (file.length() == 0) {
          continue;
        }

        String title;
        try (BufferedReader titleReader =
            new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF_8))) {
          title = titleReader.readLine();
          if (title != null && title.startsWith("[[")) {
            // Generally the first line of the txt is the title. In a few cases the
            // first line is a "[[tag]]" and the second line is the title.
            title = titleReader.readLine();
          }
        }
        Matcher matcher = SECTION_HEADER.matcher(title);
        if (matcher.matches()) {
          title = matcher.group(1);
        }

        String outputFile = AsciiDoctor.mapInFileToOutFile(inputFile, inExt, outExt);
        try (FileReader reader = new FileReader(file)) {
          Document doc = new Document();
          doc.add(new TextField(Constants.DOC_FIELD, reader));
          doc.add(new StringField(Constants.URL_FIELD, prefix + outputFile, Field.Store.YES));
          doc.add(new TextField(Constants.TITLE_FIELD, title, Field.Store.YES));
          iwriter.addDocument(doc);
        }
      }
    }
    return directory;
  }

  private byte[] zip(RAMDirectory dir) throws IOException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(buf)) {
      for (String name : dir.listAll()) {
        try (IndexInput in = dir.openInput(name, null)) {
          int len = (int) in.length();
          byte[] tmp = new byte[len];
          ZipEntry entry = new ZipEntry(name);
          entry.setSize(len);
          in.readBytes(tmp, 0, len);
          zip.putNextEntry(entry);
          zip.write(tmp, 0, len);
          zip.closeEntry();
        }
      }
    }

    return buf.toByteArray();
  }

  public static void main(String[] args) {
    try {
      new DocIndexer().invoke(args);
    } catch (IOException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
  }
}
