/*
 * Copyright (C) 2013 Google, Inc.
 * Copyright (C) 2013 Square, Inc.
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
package test;

import org.junit.Assert;
import org.junit.Test;
/*
import test.Factories.A;
import test.Factories.B;
import test.Factories.C;
import test.Factories.D;
import test.Factories.E;
import test.Factories.RootModule;
*/

public class AppTest {
  @Test public void factories() {
    /*
    Provider<A> a = new Provider<A>() {
      @Override A get() { return null; }
    };
    B.BFactory b = new B.BFactory.Impl(a);

    Factories factories = root.get(Factories.class);
    Assert.assertEquals("String", factories.bF.makeB("String").s);
    Assert.assertEquals(a, factories.bF.makeB("String").a);
    Assert.assertEquals("String", factories.cF.makeC("String").s);
    // C doesn't have an A.
    Assert.assertEquals("String", factories.dF.create("String").s);
    Assert.assertEquals(a, factories.dF.create("String").a);
    Assert.assertEquals("String", factories.eF.create("String").s);
    Assert.assertEquals(a, factories.eF.create("String").a);
    */
  } 
}
