package org.jetbrains.plugins.cucumber.java.actions;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import org.jetbrains.plugins.cucumber.CucumberCodeInsightTestCase;
import org.jetbrains.plugins.cucumber.java.CucumberJavaTestUtil;

/**
 * User: zolotov
 * Date: 10/7/13
 */
@TestDataPath("$CONTENT_ROOT/testData/selectWord")
public class GherkinStepParameterSelectionerTest extends CucumberCodeInsightTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.configureByFile("MyStepdefs.java");
  }

  public void testStepWithQuotedString() throws Exception {
    doTest();
  }

  public void testScenarioStepWithTag() throws Exception {
    doTest();
  }
  
  private void doTest() {
    CodeInsightTestUtil.doWordSelectionTestOnDirectory(myFixture, getTestName(true), "feature");
  }

  @Override
  protected String getBasePath() {
    return CucumberJavaTestUtil.RELATED_TEST_DATA_PATH + "selectWord";
  }
  
    @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return CucumberJavaTestUtil.createCucumberProjectDescriptor();
  }
}
