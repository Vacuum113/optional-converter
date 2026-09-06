package com.optionalconverter;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class OptionalChainConverterTest extends LightJavaCodeInsightFixtureTestCase {

    private void doTest(String before, String after) {
        PsiFile file = myFixture.configureByText("Test.java", before);
        int offset = before.indexOf("<caret>");
        String cleanBefore = before.replace("<caret>", "");
        file = myFixture.configureByText("Test.java", cleanBefore);

        PsiElement element = file.findElementAt(offset);
        assertNotNull("Element at caret not found", element);

        OptionalChainConverter.ChainInfo chain = OptionalChainConverter.findChain(element);
        assertNotNull("Chain not found at caret", chain);

        WriteCommandAction.runWriteCommandAction(getProject(), () ->
                OptionalChainConverter.convert(getProject(), chain));

        assertEquals(after, file.getText());
    }

    private void doTestNotAvailable(String code) {
        int offset = code.indexOf("<caret>");
        String clean = code.replace("<caret>", "");
        PsiFile file = myFixture.configureByText("Test.java", clean);

        PsiElement element = file.findElementAt(offset);
        if (element == null) return;

        OptionalChainConverter.ChainInfo chain = OptionalChainConverter.findChain(element);
        assertNull("Chain should not be found", chain);
    }

    // --- Basic chain conversion ---

    public void testSimpleChain() {
        doTest(
                "class Test { void m(A a) { a.<caret>getB().getC(); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { C getC() { return null; } }\n" +
                "class C {}",

                "class Test { void m(A a) { java.util.Optional.ofNullable(a).map(A::getB).map(B::getC).orElse(null); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { C getC() { return null; } }\n" +
                "class C {}"
        );
    }

    public void testThreeCallChain() {
        doTest(
                "class Test { void m(A a) { a.<caret>getB().getC().getD(); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { C getC() { return null; } }\n" +
                "class C { String getD() { return null; } }",

                "class Test { void m(A a) { java.util.Optional.ofNullable(a).map(A::getB).map(B::getC).map(C::getD).orElse(null); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { C getC() { return null; } }\n" +
                "class C { String getD() { return null; } }"
        );
    }

    public void testSingleCall() {
        doTest(
                "class Test { void m(A a) { a.<caret>getB(); } }\n" +
                "class A { String getB() { return null; } }",

                "class Test { void m(A a) { java.util.Optional.ofNullable(a).map(A::getB).orElse(null); } }\n" +
                "class A { String getB() { return null; } }"
        );
    }

    // --- Implicit root (no qualifier on first call) ---

    public void testImplicitRoot() {
        doTest(
                "class Test { B getA() { return null; } void m() { <caret>getA().getB(); } }\n" +
                "class B { String getB() { return null; } }",

                "class Test { B getA() { return null; } void m() { java.util.Optional.ofNullable(getA()).map(B::getB).orElse(null); } }\n" +
                "class B { String getB() { return null; } }"
        );
    }

    // --- Nullability: ofNullable vs of ---

    public void testNewExpressionUsesOf() {
        doTest(
                "class Test { void m() { new A().<caret>getB().getC(); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { String getC() { return null; } }",

                "class Test { void m() { java.util.Optional.of(new A()).map(A::getB).map(B::getC).orElse(null); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { String getC() { return null; } }"
        );
    }

    public void testThisUsesOf() {
        doTest(
                "class Test { B getB() { return null; } void m() { this.<caret>getB().getC(); } }\n" +
                "class B { String getC() { return null; } }",

                "class Test { B getB() { return null; } void m() { java.util.Optional.of(this).map(Test::getB).map(B::getC).orElse(null); } }\n" +
                "class B { String getC() { return null; } }"
        );
    }

    public void testEffectivelyFinalLocalUsesOf() {
        doTest(
                "class Test { void m() { A a = new A(); a.<caret>getB().getC(); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { String getC() { return null; } }",

                "class Test { void m() { A a = new A(); java.util.Optional.of(a).map(A::getB).map(B::getC).orElse(null); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { String getC() { return null; } }"
        );
    }

    public void testReassignedLocalUsesOfNullable() {
        doTest(
                "class Test { void m() { A a = new A(); a = null; a.<caret>getB().getC(); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { String getC() { return null; } }",

                "class Test { void m() { A a = new A(); a = null; java.util.Optional.ofNullable(a).map(A::getB).map(B::getC).orElse(null); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { String getC() { return null; } }"
        );
    }

    public void testParameterWithoutAnnotationUsesOfNullable() {
        doTest(
                "class Test { void m(A a) { a.<caret>getB().getC(); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { String getC() { return null; } }",

                "class Test { void m(A a) { java.util.Optional.ofNullable(a).map(A::getB).map(B::getC).orElse(null); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { String getC() { return null; } }"
        );
    }

    public void testParameterWithNotNullUsesOf() {
        doTest(
                "import org.jetbrains.annotations.NotNull;\n" +
                "class Test { void m(@NotNull A a) { a.<caret>getB().getC(); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { String getC() { return null; } }",

                "import org.jetbrains.annotations.NotNull;\n" +
                "class Test { void m(@NotNull A a) { java.util.Optional.of(a).map(A::getB).map(B::getC).orElse(null); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { String getC() { return null; } }"
        );
    }

    public void testFinalFieldWithNewUsesOf() {
        doTest(
                "class Test { final A a = new A(); void m() { a.<caret>getB().getC(); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { String getC() { return null; } }",

                "class Test { final A a = new A(); void m() { java.util.Optional.of(a).map(A::getB).map(B::getC).orElse(null); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { String getC() { return null; } }"
        );
    }

    public void testSuperUsesOf() {
        doTest(
                "class Base { B getB() { return null; } }\n" +
                "class Test extends Base { void m() { super.<caret>getB().getC(); } }\n" +
                "class B { String getC() { return null; } }",

                "class Base { B getB() { return null; } }\n" +
                "class Test extends Base { void m() { java.util.Optional.of(super).map(Base::getB).map(B::getC).orElse(null); } }\n" +
                "class B { String getC() { return null; } }"
        );
    }

    public void testLiteralUsesOf() {
        doTest(
                "class Test { void m() { \"hello\".<caret>toString().trim(); } }",
                "class Test { void m() { java.util.Optional.of(\"hello\").map(v -> v.toString()).map(v -> v.trim()).orElse(null); } }"
        );
    }

    public void testNonFinalFieldUsesOfNullable() {
        doTest(
                "class Test { A a; void m() { a.<caret>getB().getC(); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { String getC() { return null; } }",

                "class Test { A a; void m() { java.util.Optional.ofNullable(a).map(A::getB).map(B::getC).orElse(null); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { String getC() { return null; } }"
        );
    }

    public void testChainAfterMethodWithArgs() {
        doTest(
                "class Test { void m(A a) { a.getB(1).<caret>getC().getD(); } }\n" +
                "class A { B getB(int x) { return null; } }\n" +
                "class B { C getC() { return null; } }\n" +
                "class C { String getD() { return null; } }",

                "class Test { void m(A a) { java.util.Optional.ofNullable(a.getB(1)).map(B::getC).map(C::getD).orElse(null); } }\n" +
                "class A { B getB(int x) { return null; } }\n" +
                "class B { C getC() { return null; } }\n" +
                "class C { String getD() { return null; } }"
        );
    }

    public void testNotNullMethodReturnUsesOf() {
        doTest(
                "import org.jetbrains.annotations.NotNull;\n" +
                "class Test { void m() { getA().<caret>getB().getC(); }\n" +
                "  @NotNull A getA() { return new A(); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { String getC() { return null; } }",

                "import org.jetbrains.annotations.NotNull;\n" +
                "class Test { void m() { java.util.Optional.of(getA()).map(A::getB).map(B::getC).orElse(null); }\n" +
                "  @NotNull A getA() { return new A(); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { String getC() { return null; } }"
        );
    }

    public void testGenericTypeFallsBackToLambda() {
        doTest(
                "class Test { <T extends A> void m(T t) { t.<caret>getB().getC(); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { String getC() { return null; } }",

                "class Test { <T extends A> void m(T t) { java.util.Optional.ofNullable(t).map(v -> v.getB()).map(B::getC).orElse(null); } }\n" +
                "class A { B getB() { return null; } }\n" +
                "class B { String getC() { return null; } }"
        );
    }

    // --- Not available cases ---

    public void testNotAvailableOnMethodWithArgs() {
        doTestNotAvailable(
                "class Test { void m(A a) { a.<caret>getB(1).getC(); } }\n" +
                "class A { B getB(int x) { return null; } }\n" +
                "class B { String getC() { return null; } }"
        );
    }

    public void testNotAvailableOnStaticCall() {
        doTestNotAvailable(
                "class Test { void m() { <caret>A.getB().getC(); } }\n" +
                "class A { static B getB() { return null; } }\n" +
                "class B { String getC() { return null; } }"
        );
    }

    public void testNotAvailableOnSingleCallWithoutQualifier() {
        doTestNotAvailable(
                "class Test { String getA() { return null; } void m() { <caret>getA(); } }"
        );
    }
}
