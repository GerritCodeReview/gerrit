import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.AttributesBuilder;
import org.asciidoctor.Options;
import org.asciidoctor.OptionsBuilder;
import org.asciidoctor.internal.JRubyAsciidoctor;
import org.asciidoctor.internal.RubyHashUtil;
import org.jruby.RubySymbol;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

public class Main {

  private class CliOptions {

    private static final char ATTRIBUTE_SEPARATOR = '=';
    public static final String MONITOR_OPTION_NAME = "monitor";

    @Parameter(names = {"-b", "--backend"}, description = "set output format backend (default: html5)")
    private String backend = "html5";

    @Parameter(names = {"-d", "--doctype"},  description = "document type to use when rendering output: [article, book, inline] (default: article)")
    private String doctype = "article";

    @Parameter(names = {"-o", "--out-file"}, description = "output file (default: based on input file path); use - to output to STDOUT")
    private String outFile;

    @Parameter(names = {"-s", "--no-header-footer"}, description = "suppress output of header and footer (default: false)")
    private boolean noHeaderFooter = false;

    @Parameter(names = {"-n", "--section-numbers"}, description = "auto-number section titles in the HTML backend; disabled by default")
    private boolean sectionNumbers = false;

    @Parameter(names = {"-e", "--eruby"}, description = "specify eRuby implementation to render built-in templates: [erb, erubis] (default: erb)")
    private String eruby = "erb";

    @Parameter(names = {"-C", "--compact"}, description = "compact the output by removing blank lines (default: false)")
    private boolean compact = false;

    @Parameter(names = {"-T", "--template-dir"}, description = "directory containing custom render templates the override the built-in set")
    private String templateDir;

    @Parameter(names = {"-B", "--base-dir"}, description = "base directory containing the document and resources (default: directory of source file)")
    private String baseDir;

    @Parameter(names = {"-D", "--destination-dir"}, description = "destination output directory (default: directory of source file)")
    private String destinationDir;

    @Parameter(names = {"--trace"}, description = "include backtrace information on errors (default: false)")
    private boolean trace = false;

    @Parameter(names = {"-m", "--multi-out"}, description = "generate multiple output files when there are multiple input files (default: true)")
    private boolean multiOut = true;

    @Parameter(names = {"--in-ext"}, description = "extension for input files, only used with multi-out (default: .txt)")
    private String inExt = ".txt";

    @Parameter(names = {"--out-ext"}, description = "extension for output files, only used with multi-out (default: .html)")
    private String outExt = ".html";

    @Parameter(names = {"-h", "--help"}, help = true, description = "show this message")
    private boolean help = false;

    @Parameter(names = {"-a", "--attribute"}, description = "a list of attributes, in the form key or key=value pair, to set on the document")
    private List<String> attributes = new ArrayList<String>();

    @Parameter(description = "input files")
    private List<String> parameters = new ArrayList<String>();

    public List<String> getParameters() {
      return parameters;
    }

    public String getBackend() {
      return backend;
    }

    public String getDoctype() {
      return doctype;
    }

    public String getOutFile() {
      return outFile;
    }

    public String getOutFile(String inFile) {
      if (!multiOut || parameters.size() == 1) {
        return outFile;
      }
      String dir;
      if (destinationDir != null) {
        dir = destinationDir + File.separatorChar;
      } else {
        File f = new File(outFile);
        dir = f.getParentFile().getAbsolutePath() + File.separatorChar;
      }
      String basename = new File(inFile).getName();
      if (basename.endsWith(inExt)) {
        basename = basename.substring(0, basename.length() - inExt.length());
      } else {
        // Strip out the last extension
        int pos = basename.lastIndexOf(".");
        if (pos > 0) {
          basename = basename.substring(0, pos);
        }
      }
      return dir + basename + outExt;
    }

    public boolean isOutFileOption() {
      return outFile != null;
    }

    public boolean isNoHeaderFooter() {
      return noHeaderFooter;
    }

    public boolean isSectionNumbers() {
      return sectionNumbers;
    }

    public String getEruby() {
      return eruby;
    }

    public boolean isCompact() {
      return compact;
    }

    public String getTemplateDir() {
      return templateDir;
    }

    public boolean isTemplateDirOption() {
      return templateDir != null;
    }

    public String getBaseDir() {
      return baseDir;
    }

    public boolean isBaseDirOption() {
      return baseDir != null;
    }

    public String getDestinationDir() {
      return destinationDir;
    }

    public boolean isDestinationDirOption() {
      return destinationDir != null;
    }

    public boolean isTrace() {
      return trace;
    }

    public boolean isMultiOut() {
      return multiOut;
    }

    public boolean isHelp() {
      return help;
    }

    private boolean isOutputStdout() {
      return "-".equals(getOutFile());
    }

    private boolean isInPlaceRequired() {
      return !isOutFileOption() && !isDestinationDirOption() && !isOutputStdout();
    }

    public Options parse(String inputFile) {
      OptionsBuilder optionsBuilder = OptionsBuilder.options();
      AttributesBuilder attributesBuilder = AttributesBuilder.attributes();

      optionsBuilder.backend(this.backend).docType(doctype).eruby(eruby);

      if(isOutFileOption() && !isOutputStdout()) {
        optionsBuilder.toFile(new File(getOutFile(inputFile)));
      }

      if(this.noHeaderFooter) {
        optionsBuilder.headerFooter(false);
      }

      if(this.sectionNumbers) {
        attributesBuilder.sectionNumbers(this.sectionNumbers);
      }

      if(this.compact) {
        optionsBuilder.compact(this.compact);
      }

      if(isBaseDirOption()) {
        optionsBuilder.baseDir(new File(this.baseDir));
      }

      if(isTemplateDirOption()) {
        optionsBuilder.templateDir(new File(this.templateDir));
      }

      if(isDestinationDirOption() && !isOutputStdout()) {
        optionsBuilder.toDir(new File(this.destinationDir));
      }

      if(isInPlaceRequired()) {
        optionsBuilder.inPlace(true);
      }

      attributesBuilder.attributes(getAttributes());
      optionsBuilder.attributes(attributesBuilder.get());
      return optionsBuilder.get();

    }

    private Map<String, Object> getAttributes() {

      Map<String, Object> attributeValues = new HashMap<String, Object>();

      for (String attribute : this.attributes) {
        int equalsIndex = -1;
        if((equalsIndex = attribute.indexOf(ATTRIBUTE_SEPARATOR)) > -1) {
          extractAttributeNameAndValue(attributeValues, attribute, equalsIndex);
        } else {
          attributeValues.put(attribute, "");
        }
      }

      return attributeValues;
    }

    private void extractAttributeNameAndValue(Map<String, Object> attributeValues, String attribute, int equalsIndex) {
      String attributeName = attribute.substring(0, equalsIndex);
      String attributeValue = attribute.substring(equalsIndex+1, attribute.length());

      attributeValues.put(attributeName, attributeValue);
    }
  }

  public void invoke(String... parameters) {

    CliOptions cliOptions = new CliOptions();
    JCommander jCommander = new JCommander(cliOptions, parameters);

    if (cliOptions.isHelp()) {
      jCommander.setProgramName("asciidoctor");
      jCommander.usage();
    } else {

      List<String> inputFiles = getInputFiles(cliOptions);

      for (String inputFile : inputFiles) {
        Options options = cliOptions.parse(inputFile);
        String output = renderInput(options, inputFile);

        if(output != null) {
          System.out.println(output);
        }
      }

      if (cliOptions.isMultiOut()) {
        File file = new File(cliOptions.getOutFile());
        if (file.exists()) {
          file.setLastModified(System.currentTimeMillis());
        } else {
          file.getParentFile().mkdirs();
          try {
            FileWriter writer = new FileWriter(file, true);
            writer.close();
          } catch (java.io.IOException e) {
            throw new IllegalArgumentException(String.format(
                  "asciidoctor: FAILED: can't touch output file \"%s\"", cliOptions.getOutFile()));
          }
        }
      }
    }
  }

  private String renderInput(Options options, String inputFile) {
    Asciidoctor asciidoctor = JRubyAsciidoctor.create();

    return asciidoctor.renderFile(new File(inputFile), options);
  }

  private List<String> getInputFiles(CliOptions cliOptions) {

    List<String> parameters = cliOptions.getParameters();

    if (parameters.isEmpty()) {
      throw new IllegalArgumentException("asciidoctor: FAILED: input file missing");
    }

    if (parameters.size() > 1 && !cliOptions.isMultiOut()) {
      throw new IllegalArgumentException("asciidoctor: FAILED: extra arguments detected (unparsed arguments: "
          + parameters);
    }

    return parameters;

  }

  public static void main(String args[]) {
    new Main().invoke(args);
  }

}
