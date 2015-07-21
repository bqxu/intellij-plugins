/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.osgi.highlighting

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import org.osmorc.LightOsgiFixtureTestCase

class UnusedDeclarationInspectionTest : LightOsgiFixtureTestCase() {
  fun testActivator() {
    doTest("""
        package pkg;
        import org.osgi.framework.*;
        public class C implements BundleActivator {
          public void start(BundleContext context) throws Exception { }
          public void stop(BundleContext context) throws Exception { }
        }""")
  }

  private fun doTest(text: String) {
    myFixture.enableInspections(UnusedDeclarationInspection(true))
    myFixture.configureByText("C.java", text)
    myFixture.checkHighlighting()
  }
}