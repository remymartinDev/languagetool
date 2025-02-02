/* LanguageTool, a natural language style checker 
 * Copyright (C) 2020 Jaume Ortolà (http://www.languagetool.org)
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
package org.languagetool.rules.pt;

import org.junit.Test;
import org.languagetool.JLanguageTool;
import org.languagetool.Languages;
import org.languagetool.TestTools;
import org.languagetool.rules.RuleMatch;
import org.languagetool.rules.spelling.hunspell.HunspellRule;

import static org.junit.Assert.assertEquals;

public class HunspellRuleTest {

  @Test
  public void testRule() throws Exception {
    HunspellRule rule = new HunspellRule(TestTools.getMessages("pt"), Languages.getLanguageForShortCode("pt-PT"), null);
    JLanguageTool langTool = new JLanguageTool(Languages.getLanguageForShortCode("pt-PT"));
    
    assertEquals(0, rule.match(langTool.getAnalyzedSentence("A família.")).length);
    RuleMatch[] matches = rule.match(langTool.getAnalyzedSentence("A familia.")); 
    assertEquals(1, matches.length);
    assertEquals("família", matches[0].getSuggestedReplacements().get(0));
    assertEquals("familiar", matches[0].getSuggestedReplacements().get(1));
    
    assertEquals(0, rule.match(langTool.getAnalyzedSentence("Covid-19, COVID-19, covid-19.")).length);
  }
}
