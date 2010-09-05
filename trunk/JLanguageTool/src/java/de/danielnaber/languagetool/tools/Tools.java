/* LanguageTool, a natural language style checker 
 * Copyright (C) 2006 Daniel Naber (http://www.danielnaber.de)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package de.danielnaber.languagetool.tools;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import de.danielnaber.languagetool.AnalyzedSentence;
import de.danielnaber.languagetool.JLanguageTool;
import de.danielnaber.languagetool.Language;
import de.danielnaber.languagetool.bitext.BitextReader;
import de.danielnaber.languagetool.bitext.StringPair;
import de.danielnaber.languagetool.bitext.TabBitextReader;
import de.danielnaber.languagetool.rules.RuleMatch;
import de.danielnaber.languagetool.rules.Rule;
import de.danielnaber.languagetool.rules.bitext.BitextRule;
import de.danielnaber.languagetool.rules.bitext.pattern.BitextPatternRuleLoader;
import de.danielnaber.languagetool.rules.bitext.pattern.FalseFriendsAsBitextLoader;
import de.danielnaber.languagetool.rules.patterns.PatternRule;
import de.danielnaber.languagetool.tools.StringTools.XmlPrintMode;

public final class Tools {

  private static final int DEFAULT_CONTEXT_SIZE = 45;

  private Tools() {
    // cannot construct, static methods only
  }

  /**
   * Tags text using the LanguageTool tagger.
   * 
   * @param contents
   *          Text to tag.
   * @param lt
   *          LanguageTool instance
   * @throws IOException
   */
  public static void tagText(final String contents, final JLanguageTool lt)
  throws IOException {
    AnalyzedSentence analyzedText;
    final List<String> sentences = lt.sentenceTokenize(contents);
    for (final String sentence : sentences) {
      analyzedText = lt.getAnalyzedSentence(sentence);
      System.out.println(analyzedText.toString());
    }
  }

  public static int checkText(final String contents, final JLanguageTool lt)
  throws IOException {
    return checkText(contents, lt, false, -1, 0, 0, StringTools.XmlPrintMode.NORMAL_XML);
  }

  public static int checkText(final String contents, final JLanguageTool lt, final int lineOffset)
  throws IOException {
    return checkText(contents, lt, false, -1, lineOffset, 0, StringTools.XmlPrintMode.NORMAL_XML);
  }

  public static int checkText(final String contents, final JLanguageTool lt,
      final boolean apiFormat, final int lineOffset) throws IOException {
    return checkText(contents, lt, apiFormat, -1, lineOffset, 0, StringTools.XmlPrintMode.NORMAL_XML);
  }

  /**
   * Check the given text and print results to System.out.
   * 
   * @param contents
   *          a text to check (may be more than one sentence)
   * @param lt
   *        Initialized LanguageTool
   * @param apiFormat
   *          whether to print the result in a simple XML format
   * @param contextSize
   *          error text context size: -1 for default
   * @param lineOffset
   *          line number offset to be added to line numbers in matches
   * @param prevMatches
   *          number of previously matched rules
   * @param xmlMode
   *          mode of xml printout for simple xml output
   * @return
   *      Number of rule matches to the input text.
   * @throws IOException
   */
  public static int checkText(final String contents, final JLanguageTool lt,
      final boolean apiFormat, int contextSize, final int lineOffset, 
      final int prevMatches, final XmlPrintMode xmlMode) throws IOException {
    if (contextSize == -1) {
      contextSize = DEFAULT_CONTEXT_SIZE;
    }
    final long startTime = System.currentTimeMillis();
    final List<RuleMatch> ruleMatches = lt.check(contents);
    // adjust line numbers
    for (RuleMatch r : ruleMatches) {
      r.setLine(r.getLine() + lineOffset);
      r.setEndLine(r.getEndLine() + lineOffset);
    }
    if (apiFormat) {
      final String xml = StringTools.ruleMatchesToXML(ruleMatches, contents,
          contextSize, xmlMode);
      System.out.print(xml);
    } else {
      printMatches(ruleMatches, prevMatches, contents, contextSize);
    }

    //display stats if it's not in a buffered mode
    if (xmlMode == StringTools.XmlPrintMode.NORMAL_XML) {
      displayTimeStats(startTime, lt.getSentenceCount(), apiFormat);
    }
    return ruleMatches.size();
  }
  
  private static void displayTimeStats(final long startTime, 
      final long sentCount, final boolean apiFormat) {
    final long endTime = System.currentTimeMillis();
    final long time = endTime - startTime;
    final float timeInSeconds = time / 1000.0f;
    final float sentencesPerSecond = sentCount / timeInSeconds;
    if (apiFormat) {
      System.out.println("<!--");
    }
    System.out.printf(Locale.ENGLISH,
        "Time: %dms for %d sentences (%.1f sentences/sec)", time, 
        sentCount, sentencesPerSecond);
    System.out.println();
    if (apiFormat) {
      System.out.println("-->");
    }
  }
  
  /**
   * Displays matches in a simple text format.
   * @param ruleMatches Matches from rules.
   * @param prevMatches Number of previously found matches.
   * @param contents  The text that was checked.
   * @param contextSize The size of contents displayed.
   * @since 1.0.1
   */
  private static void printMatches(final List<RuleMatch> ruleMatches,
      final int prevMatches, final String contents, final int contextSize) {
    int i = 1;
    for (final RuleMatch match : ruleMatches) {
      String output = i + prevMatches + ".) Line " + (match.getLine() + 1) + ", column "
      + match.getColumn() + ", Rule ID: " + match.getRule().getId();
      if (match.getRule() instanceof PatternRule) {
        final PatternRule pRule = (PatternRule) match.getRule();
        output += "[" + pRule.getSubId() + "]";
      }
      System.out.println(output);
      String msg = match.getMessage();
      msg = msg.replaceAll("<suggestion>", "'");
      msg = msg.replaceAll("</suggestion>", "'");
      System.out.println("Message: " + msg);
      final List<String> replacements = match.getSuggestedReplacements();
      if (!replacements.isEmpty()) {
        System.out.println("Suggestion: "
            + StringTools.listToString(replacements, "; "));
      }
      System.out.println(StringTools.getContext(match.getFromPos(), match
          .getToPos(), contents, contextSize));
      if (i < ruleMatches.size()) {
        System.out.println();
      }
      i++;
    }
  }
  
  /**
   * Checks the bilingual input (bitext) and displays the output (considering the target 
   * language) in API format or in the simple text format.
   * 
   * @param src   Source text.
   * @param trg   Target text.
   * @param srcLt Source JLanguageTool (used to analyze the text).
   * @param trgLt Target JLanguageTool (used to analyze the text).
   * @param bRules  Bilingual rules used in addition to target standard rules.
   * @param apiFormat Whether API format should be used.
   * @param xmlMode The mode of XML output display.
   * @return  The number of rules matched on the bitext.
   * @throws IOException
   * @since 1.0.1
   */
  public static int checkBitext(final String src, final String trg,
      final JLanguageTool srcLt, final JLanguageTool trgLt,
      final List<BitextRule> bRules,
      final boolean apiFormat, final XmlPrintMode xmlMode) throws IOException {
    final long startTime = System.currentTimeMillis();
    final int contextSize = DEFAULT_CONTEXT_SIZE;
    final List<RuleMatch> ruleMatches = 
      checkBitext(src, trg, srcLt, trgLt, bRules);
    //FIXME: get current line number, column number etc. from the 
    //bitext reader, refactor the method to take the bitext reader
    //instead of pure strings as input
    for (RuleMatch thisMatch : ruleMatches) {
      thisMatch = 
        trgLt.adjustRuleMatchPos(thisMatch, 
            0, 0, 0, src + "\t" + trg);
    }
    if (apiFormat) {
      final String xml = StringTools.ruleMatchesToXML(ruleMatches, trg,
          contextSize, xmlMode);
      System.out.print(xml);
    } else {
      printMatches(ruleMatches, 0, trg, contextSize);
    }
    //display stats if it's not in a buffered mode
    if (xmlMode == StringTools.XmlPrintMode.NORMAL_XML) {
      displayTimeStats(startTime, srcLt.getSentenceCount(), apiFormat);
    }
    return ruleMatches.size();
  }
   
  public static int checkBitext(final BitextReader reader,
      final JLanguageTool srcLt, final JLanguageTool trgLt,
      final List<BitextRule> bRules,
      final boolean apiFormat, final XmlPrintMode xmlMode) throws IOException {
    final long startTime = System.currentTimeMillis();
    final int contextSize = DEFAULT_CONTEXT_SIZE;
    final List<RuleMatch> ruleMatches = new ArrayList<RuleMatch>();
    for (StringPair srcAndTrg : reader) {
      List<RuleMatch> curMatches = checkBitext(
          srcAndTrg.getSource(), srcAndTrg.getTarget(), 
          srcLt, trgLt, bRules);
      /*
      for (RuleMatch thisMatch : ruleMatches) {
        thisMatch = 
          trgLt.adjustRuleMatchPos(thisMatch, 
              reader.getSentencePosition(), 
              reader.getColumnCount(), 
              reader.getLineCount(), 
              srcAndTrg.getTarget());
      }
      */
      ruleMatches.addAll(curMatches);
      if (apiFormat) {
        final String xml = StringTools.ruleMatchesToXML(ruleMatches, 
            srcAndTrg.getTarget(),
            contextSize, xmlMode);
        System.out.print(xml);
      } else {
        printMatches(ruleMatches, 0, srcAndTrg.getTarget(), contextSize);
      }
    }       
    //display stats if it's not in a buffered mode
    if (xmlMode == StringTools.XmlPrintMode.NORMAL_XML) {
      displayTimeStats(startTime, srcLt.getSentenceCount(), apiFormat);
    }
    return ruleMatches.size();
  }
  
  /**
  * Checks the bilingual input (bitext) and displays the output (considering the target 
  * language) in API format or in the simple text format.
  * 
  * @param src   Source text.
  * @param trg   Target text.
  * @param srcLt Source JLanguageTool (used to analyze the text).
  * @param trgLt Target JLanguageTool (used to analyze the text).
  * @param bRules  Bilingual rules used in addition to target standard rules.  
  * @return  The list of rule matches on the bitext.
  * @throws IOException
  * @since 1.0.1
  */
  public static List<RuleMatch> checkBitext(final String src, final String trg,
      final JLanguageTool srcLt, final JLanguageTool trgLt,
      final List<BitextRule> bRules) throws IOException {
   final List<RuleMatch> ruleMatches = srcLt.check(src);    
    for (BitextRule bRule : bRules) {
      final RuleMatch[] curMatch = bitextMatch(bRule, src, trg, srcLt, trgLt);
      if (curMatch != null) {
        ruleMatches.addAll(Arrays.asList(curMatch));
      }
    }
   return ruleMatches;
  }
  
  private static RuleMatch[] bitextMatch(final BitextRule rule, final String src, final String trg,
      final JLanguageTool srcLanguageTool,
      final JLanguageTool trgLanguageTool) throws IOException {
    final AnalyzedSentence srcText = srcLanguageTool.getAnalyzedSentence(src);
    final AnalyzedSentence trgText = trgLanguageTool.getAnalyzedSentence(trg);
    return rule.match(srcText, trgText);    
  }

  
  /** 
   * Gets default bitext rules for a given pair of languages
   * @param source  Source language.
   * @param target  Target language.
   * @return  List of Bitext rules
   * @throws IOException
   * @throws ParserConfigurationException
   * @throws SAXException
   */
  public static List<BitextRule> getBitextRules(final Language source, 
      final Language target) throws IOException, ParserConfigurationException, SAXException {
    final List<BitextRule> bRules = new ArrayList<BitextRule>();
    //try to load the bitext pattern rules for the language...
    final BitextPatternRuleLoader ruleLoader = new BitextPatternRuleLoader();          
    final String name = "/" + target.getShortName() + "/bitext.xml";
    final InputStream is = JLanguageTool.getDataBroker().getFromRulesDirAsStream(name);
    if (is != null) {
      bRules.addAll(ruleLoader.getRules(is, name));
    }
    
    //load the false friend rules in the bitext mode
    final FalseFriendsAsBitextLoader fRuleLoader = new FalseFriendsAsBitextLoader();
    final String fName = "/false-friends.xml";
    bRules.addAll(fRuleLoader.
    getFalseFriendsAsBitext(        
        JLanguageTool.getDataBroker().getRulesDir() + fName,
        source, target));    

    //load Java bitext rules
    // TODO: get ResourceBundle for possible parameters for rules
    bRules.addAll(getAllBuiltinBitextRules(source, null));
    return bRules;
  }
  
  private static List<BitextRule> getAllBuiltinBitextRules(final Language language,
      final ResourceBundle messages) {
    // use reflection to get a list of all non-pattern rules under
    // "de.danielnaber.languagetool.rules.bitext"
    // generic rules first, then language-specific ones
    // TODO: the order of loading classes is not guaranteed so we may want to
    // implement rule
    // precedence

    final List<BitextRule> rules = new ArrayList<BitextRule>();
    try {
      // we pass ".*Rule$" regexp to improve efficiency, see javadoc
      final Class[] classes = ReflectionUtils.findClasses(Rule.class
          .getClassLoader(), Rule.class.getPackage().getName()
          + ".bitext", ".*Rule$", 0,
          Rule.class, null);
            
      for (final Class class1 : classes) {
        final Constructor[] constructors = class1.getConstructors();
        for (final Constructor constructor : constructors) {
          final Class[] paramTypes = constructor.getParameterTypes();
          if (paramTypes.length == 0) {
            rules.add((BitextRule) constructor.newInstance());
            break;
          }
          if (paramTypes.length == 1
              && paramTypes[0].equals(ResourceBundle.class)) {
            rules.add((BitextRule) constructor.newInstance(messages));
            break;
          }
          if (paramTypes.length == 2
              && paramTypes[0].equals(ResourceBundle.class)
              && paramTypes[1].equals(Language.class)) {
            rules.add((BitextRule) constructor.newInstance(messages, language));
            break;
          }
          throw new RuntimeException("Unknown constructor for rule class: "
              + class1.getName());
        }
      }
    } catch (final Exception e) {
      throw new RuntimeException("Failed to load rules: " + e.getMessage(), e);
    }
    // System.err.println("Loaded " + rules.size() + " rules");
    return rules;
  }

  
  /**
   * Simple rule profiler - used to run LT on a corpus to see which
   * rule takes most time.
   * @param contents - text to check 
   * @param lt - instance of LanguageTool
   * @return number of matches
   * @throws IOException
   */
  public static void profileRulesOnText(final String contents, 
      final JLanguageTool lt) throws IOException {
    final long[] workTime = new long[10];
    int matchCount = 0;
    final List<Rule> rules = lt.getAllRules();
    final int ruleCount = rules.size();
    System.out.printf("Testing %d rules\n", ruleCount);
    System.out.println("Rule ID\tTime\tSentences\tMatches\tSentences per sec.");
    final List<String> sentences = lt.sentenceTokenize(contents);
    for (Rule rule : rules) {
      matchCount = 0;
    for (int k = 0; k < 10; k++) {
      final long startTime = System.currentTimeMillis();
      for (String sentence : sentences) {
      matchCount += rule.match
        (lt.getAnalyzedSentence(sentence)).length;
      }
      final long endTime = System.currentTimeMillis();
      workTime[k] = endTime - startTime;    
    }
    Arrays.sort(workTime);
    final long time = median(workTime);
    final float timeInSeconds = time / 1000.0f;
    final float sentencesPerSecond = sentences.size() / timeInSeconds;    
    System.out.printf(Locale.ENGLISH,
          "%s\t%d\t%d\t%d\t%.1f", rule.getId(), 
          time, sentences.size(), matchCount, sentencesPerSecond);
      System.out.println();          
    }    
  }
  
  public static int profileRulesOnLine(final String contents, 
      final JLanguageTool lt, final Rule rule) throws IOException {
    int count = 0;  
    for (final String sentence : lt.sentenceTokenize(contents)) {
      count += rule.match(lt.getAnalyzedSentence(sentence)).length ;
    }
    return count;
  } 
  
  public static long median(long[] m) {
    final int middle = m.length / 2;  // subscript of middle element
    if (m.length % 2 == 1) {
        // Odd number of elements -- return the middle one.
        return m[middle];
    } 
      return (m[middle-1] + m[middle]) / 2;
    }

  /**
   *  Automatically applies suggestions to the text.
   *  Note: if there is more than one suggestion, always the first
   *  one is applied, and others ignored silently.
   *
   *  @param
   *    contents - String to be corrected
   *  @param
   *    lt - Initialized LanguageTool object
   *  @return
   *    Corrected text as String.
   */
  public static String correctText(final String contents, final JLanguageTool lt) throws IOException {
    final List<RuleMatch> ruleMatches = lt.check(contents);
    if (ruleMatches.isEmpty()) {
      return contents;  
    }    
    return correctTextFromMatches(contents, ruleMatches);    
  }
  
  /**
   *  Automatically applies suggestions to the bilingual text.
   *  Note: if there is more than one suggestion, always the first
   *  one is applied, and others ignored silently.
   *
   *  @param   
   *    src source string to be checked
   *  @param target target string to be checked
   *  @param
   *    sourceLanguageTool Initialized source JLanguageTool object
   *  @param
   *    targetLanguageTool Initialized target JLanguageTool object
   *  @param
   *    bRules  List of all BitextRules to use
   *  @return
   *    Corrected text as String.
   */  
  public static String correctBitext(final String src, final String target,
      final JLanguageTool sourceLanguageTool, final JLanguageTool targetLanguageTool,
      final List<BitextRule> bRules) throws IOException {       
    final List<RuleMatch> ruleMatches = sourceLanguageTool.check(src);
    for (BitextRule bRule : bRules) {
      final RuleMatch[] curMatch = bitextMatch(bRule, src, target, sourceLanguageTool, targetLanguageTool);
      if (curMatch != null) {
        ruleMatches.addAll(Arrays.asList(curMatch));
      }
    }
    if (ruleMatches.isEmpty()) {
      return target;
    }
    return correctTextFromMatches(target, ruleMatches);
  }

  private static String correctTextFromMatches(
      final String contents, final List<RuleMatch> matches) {
    final StringBuilder sb = new StringBuilder(contents);
    //build error list:
    final List<String> errors = new ArrayList<String>();
    for (RuleMatch rm : matches) {
      final List<String> replacements = rm.getSuggestedReplacements();
      if (!replacements.isEmpty()) {
        errors.add(sb.substring(rm.getFromPos(), rm.getToPos()));
      }
    }
    int offset = 0;
    int counter = 0;
    for (RuleMatch rm : matches) {
      final List<String> replacements = rm.getSuggestedReplacements();
      if (!replacements.isEmpty()) {
        //make sure the error hasn't been already corrected:
        if (errors.get(counter).equals(sb.substring(rm.getFromPos() - offset, rm.getToPos() - offset))) {
          sb.replace(rm.getFromPos() - offset,
              rm.getToPos() - offset, replacements.get(0));
          offset += (rm.getToPos() - rm.getFromPos())
          - replacements.get(0).length();
        }
        counter++;
      }
    }
    return sb.toString();  
  }
  
  public static InputStream getInputStream(final String resourcePath) throws IOException {
    try {
      // try the URL first:
      final URL url = new URL(resourcePath);
      // success, load the resource.
      return url.openStream();
    } catch (final MalformedURLException e) {
      // no luck. Fallback to class loader paths.
    }
    // try file path:
    final File f = new File(resourcePath);
    if (f.exists() && f.isFile() && f.canRead()) {
      return new BufferedInputStream(new FileInputStream(f));
    }
    throw new IOException(
        "Could not open input stream from URL/resource/file: "
        + f.getAbsolutePath());
  }

  /**
   * Get a stacktrace as a string.
   */
  public static String getFullStackTrace(final Throwable e) {
    final StringWriter sw = new StringWriter();
    final PrintWriter pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    return sw.toString();
  }

  /**
   * Load a file form the classpath using getResourceAsStream().
   * 
   * @param filename
   * @return the stream of the file
   * @throws IOException
   *           if the file cannot be loaded
   */
  public static InputStream getStream(final String filename) throws IOException {
    // the other ways to load the stream like
    // "Tools.class.getClass().getResourceAsStream(filename)"
    // don't work in a web context (using Grails):
    final InputStream is = Tools.class.getResourceAsStream(filename);
    if (is == null) {
      throw new IOException("Could not load file from classpath : " + filename);
    }
    return is;
  }

}
