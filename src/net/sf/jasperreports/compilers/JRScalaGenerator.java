/**
 * 
 */
package net.sf.jasperreports.compilers;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRExpression;
import net.sf.jasperreports.engine.JRExpressionChunk;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JRVariable;
import net.sf.jasperreports.engine.design.JRSourceCompileTask;
import net.sf.jasperreports.engine.util.JRStringUtil;

/**
 * @author spark
 *
 */
public class JRScalaGenerator {
	/**
	 *
	 */
	private static final String LF = "\n";
	private static final String DOUBLE_LF = LF + LF;
	private static final char DOT = '.';
	private static final int EXPR_MAX_COUNT_PER_METHOD = 100;
	private static final Set<String> PRIMITIVES = new HashSet<String>();
	private static final Set<String> SCALA_FN_KEYWORDS = new HashSet<String>(); 

	private static Map<Byte, String> fieldPrefixMap = null;
	private static Map<Byte, String> variablePrefixMap = null;
	private static Map<Byte, String> methodSuffixMap = null;

	static
	{
		PRIMITIVES.add("java.lang.Boolean");
		PRIMITIVES.add("java.lang.Byte");
		PRIMITIVES.add("java.lang.Character");
		PRIMITIVES.add("java.lang.Short");
		PRIMITIVES.add("java.lang.Integer");
		PRIMITIVES.add("java.lang.Long");
		PRIMITIVES.add("java.lang.Float");
		PRIMITIVES.add("java.lang.Double");
		
		SCALA_FN_KEYWORDS.add("val");
		SCALA_FN_KEYWORDS.add("var");
		SCALA_FN_KEYWORDS.add("def");
		
		fieldPrefixMap = new HashMap<Byte, String>();
		fieldPrefixMap.put(new Byte(JRExpression.EVALUATION_OLD),       "Old");
		fieldPrefixMap.put(new Byte(JRExpression.EVALUATION_ESTIMATED), "");
		fieldPrefixMap.put(new Byte(JRExpression.EVALUATION_DEFAULT),   "");
		
		variablePrefixMap = new HashMap<Byte, String>();
		variablePrefixMap.put(new Byte(JRExpression.EVALUATION_OLD),       "Old");
		variablePrefixMap.put(new Byte(JRExpression.EVALUATION_ESTIMATED), "Estimated");
		variablePrefixMap.put(new Byte(JRExpression.EVALUATION_DEFAULT),   "");
		
		methodSuffixMap = new HashMap<Byte, String>();
		methodSuffixMap.put(new Byte(JRExpression.EVALUATION_OLD),       "Old");
		methodSuffixMap.put(new Byte(JRExpression.EVALUATION_ESTIMATED), "Estimated");
		methodSuffixMap.put(new Byte(JRExpression.EVALUATION_DEFAULT),   "");
	}
	
	/**
	 *
	 */
	protected final JRSourceCompileTask sourceTask;

	protected Map<String, ? extends JRParameter> parametersMap;
	protected Map<String, JRField> fieldsMap;
	protected Map<String, JRVariable> variablesMap;
	protected JRVariable[] variables;
	
	protected JRScalaGenerator(JRSourceCompileTask sourceTask) {
		this.sourceTask = sourceTask;

		this.parametersMap = sourceTask.getParametersMap();
		this.fieldsMap = sourceTask.getFieldsMap();
		this.variablesMap = sourceTask.getVariablesMap();
		this.variables = sourceTask.getVariables();
	}

	/**
	 *
	 */
	public static String generateClass(JRSourceCompileTask sourceTask) throws JRException
	{
		JRScalaGenerator generator = new JRScalaGenerator(sourceTask);
		return generator.generateClass();
	}
	
	
	protected String generateClass() throws JRException
	{
		StringBuffer sb = new StringBuffer();

		generateClassStart(sb);

		generateDeclarations(sb);		

		generateInitMethod(sb);
		generateInitParamsMethod(sb);
		if (fieldsMap != null)
		{
			generateInitFieldsMethod(sb);
		}
		generateInitVarsMethod(sb);

		List<JRExpression> expressions = sourceTask.getExpressions();
		sb.append(this.generateMethod(JRExpression.EVALUATION_DEFAULT, expressions));
		if (sourceTask.isOnlyDefaultEvaluation())
		{
			List<JRExpression> empty = new ArrayList<JRExpression>();
			sb.append(this.generateMethod(JRExpression.EVALUATION_OLD, empty));
			sb.append(this.generateMethod(JRExpression.EVALUATION_ESTIMATED, empty));
		}
		else
		{
			sb.append(this.generateMethod(JRExpression.EVALUATION_OLD, expressions));
			sb.append(this.generateMethod(JRExpression.EVALUATION_ESTIMATED, expressions));
		}

		sb.append("}\n");
		
		return sb.toString();
	}


	private void generateInitMethod(StringBuffer sb)
	{
		sb.append(LF);
		sb.append(LF);
		sb.append("    /**\n");
		sb.append("     *\n");
		sb.append("     */\n");
		sb.append("    override def customizedInit(pm: java.util.Map[" +
				"java.lang.String,net.sf.jasperreports.engine.fill.JRFillParameter], " +
				"fm: java.util.Map[java.lang.String,net.sf.jasperreports.engine.fill.JRFillField], " +
				"vm: java.util.Map[java.lang.String,net.sf.jasperreports.engine.fill.JRFillVariable]) = {\n");
		sb.append("        initParams(pm)\n");
		if (fieldsMap != null)
		{
			sb.append("        initFields(fm)\n");
		}
		sb.append("        initVars(vm)\n");
		sb.append("    }\n");
		sb.append(LF);
		sb.append(LF);
	}

	
	protected final void generateClassStart(StringBuffer sb)
	{
		/*   */
		sb.append("/*\n");
		sb.append(" * Generated by JasperReports - ");
		sb.append((new SimpleDateFormat()).format(new java.util.Date()));
		sb.append(LF);
		sb.append(" */\n");
		sb.append("import net.sf.jasperreports.engine._\n");
		sb.append("import net.sf.jasperreports.engine.fill._\n");
		sb.append(LF);
		sb.append("import java.util._\n");
		sb.append("import java.math._\n");
		sb.append("import java.text._\n");
		sb.append("import java.io._\n");
		sb.append("import java.net._\n");
		sb.append(LF);
		
		/*   */
		String[] imports = sourceTask.getImports();
		if (imports != null && imports.length > 0)
		{
			for (int i = 0; i < imports.length; i++)
			{
				sb.append("import ");
				sb.append(imports[i]);
				sb.append(LF);
			}
		}

		/*   */
		sb.append(LF);
		sb.append(LF);
		sb.append("/**\n");
		sb.append(" *\n");
		sb.append(" */\n");
		sb.append("class ");
		sb.append(sourceTask.getUnitName());
		sb.append(" extends JREvaluator {");
		sb.append(LF);
		sb.append(LF);
		sb.append("    /**\n");
		sb.append("     *\n");
		sb.append("     */\n");
	}


	protected final void generateDeclarations(StringBuffer sb)
	{
		if (parametersMap != null && parametersMap.size() > 0)
		{
			Collection<String> parameterNames = parametersMap.keySet();
			for (Iterator<String> it = parameterNames.iterator(); it.hasNext();)
			{
				sb.append("    private var parameter_");
				sb.append(JRStringUtil.getJavaIdentifier(it.next()));
				sb.append(":JRFillParameter = null;\n");
			}
		}
		
		if (fieldsMap != null && fieldsMap.size() > 0)
		{
			Collection<String> fieldNames = fieldsMap.keySet();
			for (Iterator<String> it = fieldNames.iterator(); it.hasNext();)
			{
				sb.append("    private var field_");
				sb.append(JRStringUtil.getJavaIdentifier(it.next()));
				sb.append(":JRFillField = null;\n");
			}
		}
		
		if (variables != null && variables.length > 0)
		{
			for (int i = 0; i < variables.length; i++)
			{
				sb.append("    private var variable_");
				sb.append(JRStringUtil.getJavaIdentifier(variables[i].getName()));
				sb.append(":JRFillVariable = null;\n");
			}
		}
	}


	protected final void generateInitParamsMethod(StringBuffer sb) throws JRException
	{
		Iterator<String> parIt = null;
		if (parametersMap != null && parametersMap.size() > 0) 
		{
			parIt = parametersMap.keySet().iterator();
		}
		else
		{
			Set<String> emptySet = Collections.emptySet();
			parIt = emptySet.iterator();
		}
		generateInitParamsMethod(sb, parIt, 0);
	}


	protected final void generateInitFieldsMethod(StringBuffer sb) throws JRException
	{
		Iterator<String> fieldIt = null;
		if (fieldsMap != null && fieldsMap.size() > 0) 
		{
			fieldIt = fieldsMap.keySet().iterator();
		}
		else
		{
			Set<String> emptySet = Collections.emptySet();
			fieldIt = emptySet.iterator();
		}
		generateInitFieldsMethod(sb, fieldIt, 0);
	}


	protected final void generateInitVarsMethod(StringBuffer sb) throws JRException
	{
		Iterator<JRVariable> varIt = null;
		if (variables != null && variables.length > 0) 
		{
			varIt = Arrays.asList(variables).iterator();
		}
		else
		{
			Set<JRVariable> emptySet = Collections.emptySet();
			varIt = emptySet.iterator();
		}
		generateInitVarsMethod(sb, varIt, 0);
	}		


	/**
	 *
	 */
	private void generateInitParamsMethod(StringBuffer sb, Iterator<String> it, int index) throws JRException
	{
		sb.append("    /**\n");
		sb.append("     *\n");
		sb.append("     */\n");
		sb.append("    def initParams");
		if(index > 0)
		{
			sb.append(index);
		}
		sb.append("(pm:java.util.Map[_,_]) = {\n");
		for (int i = 0; i < EXPR_MAX_COUNT_PER_METHOD && it.hasNext(); i++)
		{
			String parameterName = it.next();
			sb.append("        parameter_");
			sb.append(JRStringUtil.getJavaIdentifier(parameterName));
			sb.append(" = pm.get(\"");
			sb.append(JRStringUtil.escapeJavaStringLiteral(parameterName));
			sb.append("\").asInstanceOf[JRFillParameter]\n");
		}
		if(it.hasNext())
		{
			sb.append("        initParams");
			sb.append(index + 1);
			sb.append("(pm);\n");
		}
		sb.append("    }\n");
		sb.append(LF);
		sb.append(LF);

		if(it.hasNext())
		{
			generateInitParamsMethod(sb, it, index + 1);
		}
	}		


	/**
	 *
	 */
	private void generateInitFieldsMethod(StringBuffer sb, Iterator<String> it, int index) throws JRException
	{
		sb.append("    /**\n");
		sb.append("     *\n");
		sb.append("     */\n");
		sb.append("    def initFields");
		if(index > 0)
		{
			sb.append(index);
		}
		sb.append("(fm:java.util.Map[_,_]) = {\n");
		for (int i = 0; i < EXPR_MAX_COUNT_PER_METHOD && it.hasNext(); i++)
		{
			String fieldName = it.next();
			sb.append("        field_");
			sb.append(JRStringUtil.getJavaIdentifier(fieldName));
			sb.append(" = fm.get(\"");
			sb.append(JRStringUtil.escapeJavaStringLiteral(fieldName));
			sb.append("\").asInstanceOf[JRFillField]\n");
		}
		if(it.hasNext())
		{
			sb.append("        initFields");
			sb.append(index + 1);
			sb.append("(fm);\n");
		}
		sb.append("    }\n");
		sb.append(LF);
		sb.append(LF);

		if(it.hasNext())
		{
			generateInitFieldsMethod(sb, it, index + 1);
		}
	}		


	/**
	 *
	 */
	private void generateInitVarsMethod(StringBuffer sb, Iterator<JRVariable> it, int index) throws JRException
	{
		sb.append("    /**\n");
		sb.append("     *\n");
		sb.append("     */\n");
		sb.append("    def initVars");
		if(index > 0)
		{
			sb.append(index);
		}
		sb.append("(vm:java.util.Map[_,_]) = {\n");
		for (int i = 0; i < EXPR_MAX_COUNT_PER_METHOD && it.hasNext(); i++)
		{
			String variableName = (it.next()).getName();
			sb.append("        variable_");
			sb.append(JRStringUtil.getJavaIdentifier(variableName));
			sb.append(" = vm.get(\"");
			sb.append(JRStringUtil.escapeJavaStringLiteral(variableName));
			sb.append("\").asInstanceOf[JRFillVariable]\n");
		}
		if(it.hasNext())
		{
			sb.append("        initVars");
			sb.append(index + 1);
			sb.append("(vm);\n");
		}
		sb.append("    }\n");
		sb.append(LF);
		sb.append(LF);

		if(it.hasNext())
		{
			generateInitVarsMethod(sb, it, index + 1);
		}
	}		


	/**
	 *  
	 */
	protected final String generateMethod(byte evaluationType, List<JRExpression> expressionsList) throws JRException 
	{
		StringBuffer sb = new StringBuffer();
		
		if (expressionsList != null && !expressionsList.isEmpty())
		{
			sb.append(generateMethod(expressionsList.iterator(), 0, evaluationType, expressionsList.size()));
		}
		else
		{
			/*   */
			sb.append("    /**\n");
			sb.append("     *\n");
			sb.append("     */\n");
			sb.append("    def evaluate");
			sb.append(methodSuffixMap.get(new Byte(evaluationType)));
			sb.append("(id:Int) = {\n");
			sb.append("        None;\n");
			sb.append("    }\n");
			sb.append(LF);
			sb.append(LF);
		}
		
		return sb.toString();
	}


	/**
	 * 
	 */
	@SuppressWarnings("deprecation")
	private String generateMethod(Iterator<JRExpression> it, int index, byte evaluationType, int expressionCount) throws JRException 
	{
		StringBuffer sb = new StringBuffer();
		
		/*   */
		sb.append("    /**\n");
		sb.append("     *\n");
		sb.append("     */\n");
		sb.append("    def evaluate");
		sb.append(methodSuffixMap.get(new Byte(evaluationType)));
		if (index > 0)
		{
			sb.append(index);
		}
		sb.append("(id:Int) = {\n");
		int functionPlaceholder = sb.length();
		sb.append("        var value = id match {\n");
		sb.append(LF);
		
		//NB: relying on the fact that the expression id is the same as the index of the expression in the list
		int expressionIdLimit = (index + 1) * EXPR_MAX_COUNT_PER_METHOD;
		boolean nextMethod = expressionCount > expressionIdLimit;
		
		if (nextMethod)
		{
			sb.append("        if (id >= ");
			sb.append(expressionIdLimit);
			sb.append(")\n");
			sb.append("            var value = evaluate");
			sb.append(methodSuffixMap.get(new Byte(evaluationType)));
			sb.append(index + 1);
			sb.append("(id)\n");
		}
		
		for (int i = 0; it.hasNext() && i < EXPR_MAX_COUNT_PER_METHOD; i++) 
		{
			JRExpression expression = it.next();
			
			sb.append("        case ");
			sb.append(sourceTask.getExpressionId(expression));
			String[] expressionStrArr = this.generateExpression(expression, evaluationType).split(DOUBLE_LF);
			if (expressionStrArr.length > 1) {
				
				// TODO: Find a more efficient way to process Scala functions, because lots of expressions
				// and Scala functions will have a noticeably negative effect on performance
				for (int j = 0; j < expressionStrArr.length; j++) {
					for (Iterator<String> scalaKwIter = SCALA_FN_KEYWORDS.iterator(); scalaKwIter.hasNext(); ) {
						String scalaKw = scalaKwIter.next();
						if (expressionStrArr[j].startsWith(scalaKw)) {
							sb.insert(functionPlaceholder, expressionStrArr[j] + DOUBLE_LF);
							functionPlaceholder += expressionStrArr[j].length() + 2;
							break;
						}
					}
				}
			}
			String expressionStr = expressionStrArr[expressionStrArr.length - 1];
			if (expression.getValueClass() != null && expression.getValueClass().getName().startsWith("java.lang.") && !expressionStr.startsWith("new ")) {
				sb.append(" => new ");
				sb.append(expression.getValueClassName());
				sb.append("(");
				sb.append(expressionStr);
				sb.append(")\n");
			} else {
				sb.append(" => ");
				sb.append(expressionStr);
				sb.append(LF);
			}
		}

		sb.append("       }\n");
		sb.append("       value\n");
		sb.append("    }\n");
		sb.append(LF);
		sb.append(LF);
		
		if (nextMethod)
		{
			sb.append(generateMethod(it, index + 1, evaluationType, expressionCount));
		}
		
		return sb.toString();
	}


	/**
	 *
	 */
	private String generateExpression(
		JRExpression expression,
		byte evaluationType
		)
	{
		StringBuffer sb = new StringBuffer();

		JRExpressionChunk[] chunks = expression.getChunks();
		if (chunks != null && chunks.length > 0)
		{
			for(int i = 0; i < chunks.length; i++)
			{
				JRExpressionChunk chunk = chunks[i];

				String chunkText = chunk.getText();
				if (chunkText == null)
				{
					chunkText = "";
				}
				
				switch (chunk.getType())
				{
					case JRExpressionChunk.TYPE_TEXT :
					{
						sb.append(chunkText);
						break;
					}
					case JRExpressionChunk.TYPE_PARAMETER :
					{
						JRParameter jrParameter = parametersMap.get(chunkText);
	
						sb.append("(");
						/*if (!"java.lang.Object".equals(jrParameter.getValueClassName()))
						{
							sb.append("(");
							sb.append(jrParameter.getValueClassName());
							sb.append(")");
						}*/
						sb.append("parameter_");
						sb.append(JRStringUtil.getJavaIdentifier(chunkText));
						sb.append(".getValue().asInstanceOf[");
						sb.append(jrParameter.getValueClassName());
						sb.append("])");
						if (PRIMITIVES.contains(jrParameter.getValueClass().getName())) {
							sb.append(DOT);
							sb.append(jrParameter.getValueClass().getName().substring(jrParameter.getValueClass().getName().lastIndexOf(DOT) + 1).toLowerCase());
							sb.append("Value()");
						}
						sb.append(LF);
	
						break;
					}
					case JRExpressionChunk.TYPE_FIELD :
					{
						JRField jrField = fieldsMap.get(chunkText);

						sb.append("(");
						/*if (!"java.lang.Object".equals(jrField.getValueClassName()))
						{
							sb.append("(");
							sb.append(jrField.getValueClassName());
							sb.append(")");
						}*/
						sb.append("field_");
						sb.append(JRStringUtil.getJavaIdentifier(chunkText)); 
						sb.append(".get");
						sb.append(fieldPrefixMap.get(new Byte(evaluationType))); 
						sb.append("Value().asInstanceOf[");
						sb.append(jrField.getValueClassName());
						sb.append("])");
						if (PRIMITIVES.contains(jrField.getValueClass().getName())) {
							sb.append(DOT);
							sb.append(jrField.getValueClass().getName().substring(jrField.getValueClass().getName().lastIndexOf(DOT) + 1).toLowerCase());
							sb.append("Value()");
						}
						sb.append(LF);
	
						break;
					}
					case JRExpressionChunk.TYPE_VARIABLE :
					{
						JRVariable jrVariable = variablesMap.get(chunkText);
	
						sb.append("(");
						/*if (!"java.lang.Object".equals(jrVariable.getValueClassName()))
						{
							sb.append("(");
							sb.append(jrVariable.getValueClassName());
							sb.append(")"); 
						}*/
						sb.append("variable_"); 
						sb.append(JRStringUtil.getJavaIdentifier(chunkText)); 
						sb.append(".get");
						sb.append(variablePrefixMap.get(new Byte(evaluationType))); 
						sb.append("Value().asInstanceOf[");
						sb.append(jrVariable.getValueClassName());
						sb.append("])");
						if (PRIMITIVES.contains(jrVariable.getValueClass().getName())) {
							sb.append(DOT);
							sb.append(jrVariable.getValueClass().getName().substring(jrVariable.getValueClass().getName().lastIndexOf(DOT) + 1).toLowerCase());
							sb.append("Value()");
						}
						sb.append(LF);
	
						break;
					}
					case JRExpressionChunk.TYPE_RESOURCE :
					{
						sb.append("str(\"");
						sb.append(JRStringUtil.escapeJavaStringLiteral(chunkText));
						sb.append("\")");
	
						break;
					}
				}
			}
		}
		
		if (sb.length() == 0)
		{
			sb.append("None");
		}

		return sb.toString();
	}
}
