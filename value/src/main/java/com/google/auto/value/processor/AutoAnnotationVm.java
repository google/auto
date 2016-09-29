package com.google.auto.value.processor;

final class AutoAnnotationVm {

    static final String VM = "## Template for each generated AutoAnnotation_Foo_bar class.\n"
            + "## This template uses the Apache Velocity Template Language (VTL).\n"
            + "## The variables ($pkg, $props, and so on) are defined by the fields of AutoAnnotationTemplateVars.\n"
            + "##\n"
            + "## Comments, like this one, begin with ##. The comment text extends up to and including the newline\n"
            + "## character at the end of the line. So comments also serve to join a line to the next one.\n"
            + "## Velocity deletes a newline after a directive (#if, #foreach, #end etc) so ## is not needed there.\n"
            + "## That does mean that we sometimes need an extra blank line after such a directive.\n"
            + "##\n"
            + "## A post-processing step will remove unwanted spaces and blank lines, but will not join two lines.\n"
            + "\n"
            + "#macro (cloneArray $a)\n"
            + "  #if ($gwtCompatible)\n"
            + "    ${arrays}.copyOf($a, ${a}.length)\n"
            + "  #else\n"
            + "    ${a}.clone()\n"
            + "  #end\n"
            + "#end\n"
            + "\n"
            + "#if (!$pkg.empty)\n"
            + "package $pkg;\n"
            + "#end\n"
            + "\n"
            + "#foreach ($i in $imports)\n"
            + "import $i;\n"
            + "#end\n"
            + "\n"
            + "@${generated}(\"com.google.auto.value.processor.AutoAnnotationProcessor\")\n"
            + "final class $className implements $annotationName {\n"
            + "\n"
            + "## Fields\n"
            + "\n"
            + "#foreach ($m in $members)\n"
            + "  #if ($params.containsKey($m.toString()))\n"
            + "\n"
            + "  private final $m.type $m;\n"
            + "\n"
            + "  #else\n"
            + "\n"
            + "  private static final $m.type $m = $m.defaultValue;\n"
            + "\n"
            + "  #end\n"
            + "#end\n"
            + "\n"
            + "## Constructor\n"
            + "\n"
            + "  $className(\n"
            + "#foreach ($p in $params.keySet())\n"
            + "\n"
            + "      $params[$p].type $members[$p] #if ($foreach.hasNext) , #end\n"
            + "#end ) {\n"
            + "#foreach ($p in $params.keySet())\n"
            + "  #if (!$members[$p].kind.primitive)\n"
            + "\n"
            + "    if ($p == null) {\n"
            + "      throw new NullPointerException(\"Null $p\");\n"
            + "    }\n"
            + "\n"
            + "  #end\n"
            + "\n"
            + "  #if ($members[$p].kind == \"ARRAY\")\n"
            + "    #if ($params[$p].kind == \"ARRAY\")\n"
            + "\n"
            + "    this.$p = #cloneArray(${p});\n"
            + "\n"
            + "    #elseif ($members[$p].typeMirror.componentType.kind.primitive)\n"
            + "\n"
            + "    this.$p = ${members[$p].typeMirror.componentType}ArrayFromCollection($p);\n"
            + "\n"
            + "    #elseif ($members[$p].arrayOfClassWithBounds)\n"
            + "\n"
            + "    @SuppressWarnings({\"unchecked\", \"rawtypes\"})\n"
            + "    ${members[$p].componentType}[] ${p}$ = ${p}.toArray(new Class[0]);\n"
            + "    this.$p = ${p}$;\n"
            + "\n"
            + "    #else\n"
            + "\n"
            + "    this.$p = ${p}.toArray(new ${members[$p].componentType}[0]);\n"
            + "\n"
            + "    #end\n"
            + "  #else\n"
            + "\n"
            + "    this.$p = $p;\n"
            + "\n"
            + "  #end\n"
            + "#end\n"
            + "\n"
            + "  }\n"
            + "\n"
            + "## annotationType method (defined by the Annotation interface)\n"
            + "\n"
            + "  @Override\n"
            + "  public Class<? extends $annotationName> annotationType() {\n"
            + "    return ${annotationName}.class;\n"
            + "  }\n"
            + "\n"
            + "## Member getters\n"
            + "\n"
            + "#foreach ($m in $members)\n"
            + "\n"
            + "  @Override\n"
            + "  public ${m.type} ${m}() {\n"
            + "\n"
            + "  #if ($m.kind == \"ARRAY\")\n"
            + "\n"
            + "    return #cloneArray(${m});\n"
            + "\n"
            + "  #else\n"
            + "\n"
            + "    return ${m};\n"
            + "\n"
            + "  #end\n"
            + "\n"
            + "  }\n"
            + "\n"
            + "#end\n"
            + "\n"
            + "## toString\n"
            + "\n"
            + "#macro (appendMemberString $m)\n"
            + "  #if ($m.type == \"String\" || $m.type == \"java.lang.String\")\n"
            + "    #set ($appendQuotedStringMethod = \"true\")\n"
            + "\n"
            + "    appendQuoted(sb, $m) ##\n"
            + "  #elseif ($m.type == \"char\")\n"
            + "    #set ($appendQuotedCharMethod = \"true\")\n"
            + "\n"
            + "    appendQuoted(sb, $m) ##\n"
            + "  #elseif ($m.type == \"String[]\" || $m.type == \"java.lang.String[]\")\n"
            + "    #set ($appendQuotedStringArrayMethod = \"true\")\n"
            + "\n"
            + "    appendQuoted(sb, $m) ##\n"
            + "  #elseif ($m.type == \"char[]\")\n"
            + "    #set ($appendQuotedCharArrayMethod = \"true\")\n"
            + "\n"
            + "    appendQuoted(sb, $m) ##\n"
            + "  #elseif ($m.kind == \"ARRAY\")\n"
            + "\n"
            + "    sb.append(${arrays}.toString($m)) ##\n"
            + "  #else\n"
            + "\n"
            + "    sb.append($m) ##\n"
            + "  #end\n"
            + "#end\n"
            + "\n"
            + "  @Override\n"
            + "  public String toString() {\n"
            + "    StringBuilder sb = new StringBuilder(\"@$annotationFullName(\");\n"
            + "\n"
            + "  #foreach ($p in $params.keySet())\n"
            + "\n"
            + "    #if ($params.size() > 1 || $params.keySet().iterator().next() != \"value\")\n"
            + "\n"
            + "    sb.append(\"$p=\");\n"
            + "    #end\n"
            + "\n"
            + "    #appendMemberString($members[$p]);\n"
            + "\n"
            + "    #if ($foreach.hasNext)\n"
            + "\n"
            + "    sb.append(\", \");\n"
            + "    #end\n"
            + "\n"
            + "  #end\n"
            + "\n"
            + "    return sb.append(')').toString();\n"
            + "  }\n"
            + "\n"
            + "## equals\n"
            + "\n"
            + "#macro (memberEqualsThatExpression $m)\n"
            + "  #if ($m.kind == \"FLOAT\")\n"
            + "    Float.floatToIntBits($m) == Float.floatToIntBits(that.${m}()) ##\n"
            + "  #elseif ($m.kind == \"DOUBLE\")\n"
            + "    Double.doubleToLongBits($m) == Double.doubleToLongBits(that.${m}()) ##\n"
            + "  #elseif ($m.kind.primitive)\n"
            + "    $m == that.${m}() ##\n"
            + "  #elseif ($m.kind == \"ARRAY\")\n"
            + "    #if ($params.containsKey($m.toString()))\n"
            + "    ${arrays}.equals($m,\n"
            + "        (that instanceof $className)\n"
            + "            ? (($className) that).$m\n"
            + "            : that.${m}()) ##\n"
            + "    #else ## default value, so if |that| is also a $className then it has the same constant value\n"
            + "    that instanceof $className || ${arrays}.equals($m, that.${m}())\n"
            + "    #end\n"
            + "  #else\n"
            + "    ${m}.equals(that.${m}()) ##\n"
            + "  #end\n"
            + "#end\n"
            + "\n"
            + "  @Override\n"
            + "  public boolean equals(Object o) {\n"
            + "    if (o == this) {\n"
            + "      return true;\n"
            + "    }\n"
            + "    if (o instanceof $annotationName) {\n"
            + "\n"
            + "  #if ($members.isEmpty())\n"
            + "\n"
            + "      return true;\n"
            + "\n"
            + "  #else\n"
            + "\n"
            + "      $annotationName that = ($annotationName) o;\n"
            + "      return ##\n"
            + "           #foreach ($m in $members)\n"
            + "           (#memberEqualsThatExpression ($m))##\n"
            + "             #if ($foreach.hasNext)\n"
            + "\n"
            + "           && ##\n"
            + "             #end\n"
            + "           #end\n"
            + "           ;\n"
            + "  #end\n"
            + "\n"
            + "    }\n"
            + "    return false;\n"
            + "  }\n"
            + "\n"
            + "## hashCode\n"
            + "\n"
            + "#macro (memberHashCodeExpression $m)\n"
            + "  #if ($m.kind == \"LONG\")\n"
            + "    (int) (($m >>> 32) ^ $m) ##\n"
            + "  #elseif ($m.kind == \"FLOAT\")\n"
            + "    Float.floatToIntBits($m) ##\n"
            + "  #elseif ($m.kind == \"DOUBLE\")\n"
            + "    (int) ((Double.doubleToLongBits($m) >>> 32) ^ Double.doubleToLongBits($m)) ##\n"
            + "  #elseif ($m.kind == \"BOOLEAN\")\n"
            + "    $m ? 1231 : 1237 ##\n"
            + "  #elseif ($m.kind.primitive)\n"
            + "    $m ##\n"
            + "  #elseif ($m.kind == \"ARRAY\")\n"
            + "    ${arrays}.hashCode($m) ##\n"
            + "  #else\n"
            + "    ${m}.hashCode() ##\n"
            + "  #end\n"
            + "#end\n"
            + "\n"
            + "## Compute the member name's contribution to the hashcode directly in the\n"
            + "## template engine, to avoid triggering a compiler error for integer overflow.\n"
            + "#macro (memberNameHash $m)\n"
            + "  #set ($hc = 127 * $m.toString().hashCode())\n"
            + "  ${hc} ##\n"
            + "#end\n"
            + "\n"
            + "  @Override\n"
            + "  public int hashCode() {\n"
            + "  #if ($members.isEmpty())\n"
            + "\n"
            + "    return 0;\n"
            + "\n"
            + "  #else\n"
            + "\n"
            + "    return\n"
            + "    #foreach ($m in $members)\n"
            + "\n"
            + "        (#memberNameHash($m) ^ (#memberHashCodeExpression($m))) ##\n"
            + "        #if ($foreach.hasNext) + #end\n"
            + "    #end\n"
            + "        ;\n"
            + "    #foreach ($m in $members)\n"
            + "\n"
            + "    // #memberNameHash($m) is 127 * \"${m}\".hashCode()\n"
            + "    #end\n"
            + "\n"
            + "  #end\n"
            + "\n"
            + "  }\n"
            + "\n"
            + "## support functions\n"
            + "\n"
            + "#foreach ($w in $wrapperTypesUsedInCollections)\n"
            + "  #set ($prim = $w.getField(\"TYPE\").get(\"\"))\n"
            + "\n"
            + "  private static ${prim}[] ${prim}ArrayFromCollection(Collection<${w.simpleName}> c) {\n"
            + "    ${prim}[] a = new ${prim}[c.size()];\n"
            + "    int i = 0;\n"
            + "    for (${prim} x : c) {\n"
            + "      a[i++] = x;\n"
            + "    }\n"
            + "    return a;\n"
            + "  }\n"
            + "#end\n"
            + "\n"
            + "#if ($appendQuotedStringArrayMethod)\n"
            + "  #set ($appendQuotedStringMethod = \"true\")\n"
            + "\n"
            + "  private static void appendQuoted(StringBuilder sb, String[] strings) {\n"
            + "    sb.append('[');\n"
            + "    String sep = \"\";\n"
            + "    for (String s : strings) {\n"
            + "      sb.append(sep);\n"
            + "      sep = \", \";\n"
            + "      appendQuoted(sb, s);\n"
            + "    }\n"
            + "    sb.append(']');\n"
            + "  }\n"
            + "#end\n"
            + "\n"
            + "#if ($appendQuotedCharArrayMethod)\n"
            + "  #set ($appendQuotedCharMethod = \"true\")\n"
            + "\n"
            + "  private static void appendQuoted(StringBuilder sb, char[] chars) {\n"
            + "    sb.append('[');\n"
            + "    String sep = \"\";\n"
            + "    for (char c : chars) {\n"
            + "      sb.append(sep);\n"
            + "      sep = \", \";\n"
            + "      appendQuoted(sb, c);\n"
            + "    }\n"
            + "    sb.append(']');\n"
            + "  }\n"
            + "#end\n"
            + "\n"
            + "#if ($appendQuotedStringMethod)\n"
            + "  #set ($appendEscapedMethod = \"true\")\n"
            + "\n"
            + "  private static void appendQuoted(StringBuilder sb, String s) {\n"
            + "    sb.append('\"');\n"
            + "    for (int i = 0; i < s.length(); i++) {\n"
            + "      appendEscaped(sb, s.charAt(i));\n"
            + "    }\n"
            + "    sb.append('\"');\n"
            + "  }\n"
            + "#end\n"
            + "\n"
            + "#if ($appendQuotedCharMethod)\n"
            + "  #set ($appendEscapedMethod = \"true\")\n"
            + "\n"
            + "  private static void appendQuoted(StringBuilder sb, char c) {\n"
            + "    sb.append('\\'');\n"
            + "    appendEscaped(sb, c);\n"
            + "    sb.append('\\'');\n"
            + "  }\n"
            + "#end\n"
            + "\n"
            + "#if ($appendEscapedMethod)\n"
            + "  private static void appendEscaped(StringBuilder sb, char c) {\n"
            + "    switch (c) {\n"
            + "    case '\\\\':\n"
            + "    case '\"':\n"
            + "    case '\\'':\n"
            + "      sb.append('\\\\').append(c);\n"
            + "      break;\n"
            + "    case '\\n':\n"
            + "      sb.append(\"\\\\n\");\n"
            + "      break;\n"
            + "    case '\\r':\n"
            + "      sb.append(\"\\\\r\");\n"
            + "      break;\n"
            + "    case '\\t':\n"
            + "      sb.append(\"\\\\t\");\n"
            + "      break;\n"
            + "    default:\n"
            + "      if (c < 0x20) {\n"
            + "        sb.append('\\\\');\n"
            + "        appendWithZeroPadding(sb, Integer.toOctalString(c), 3);\n"
            + "      } else if (c < 0x7f || Character.isLetter(c)) {\n"
            + "        sb.append(c);\n"
            + "      } else {\n"
            + "        sb.append(\"\\\\u\");\n"
            + "        appendWithZeroPadding(sb, Integer.toHexString(c), 4);\n"
            + "      }\n"
            + "      break;\n"
            + "    }\n"
            + "  }\n"
            + "\n"
            + "  ## We use this rather than String.format because that doesn't exist on GWT.\n"
            + "\n"
            + "  private static void appendWithZeroPadding(StringBuilder sb, String s, int width) {\n"
            + "    for (int i = width - s.length(); i > 0; i--) {\n"
            + "      sb.append('0');\n"
            + "    }\n"
            + "    sb.append(s);\n"
            + "  }\n"
            + "#end\n"
            + "}\n";

    private AutoAnnotationVm() {

    }
}
