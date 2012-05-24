/**
 * 
 */
package net.sf.jasperreports.compilers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Collections;
import java.util.Iterator;

import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRReport;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.design.JRAbstractJavaCompiler;
import net.sf.jasperreports.engine.design.JRCompilationSourceCode;
import net.sf.jasperreports.engine.design.JRCompilationUnit;
import net.sf.jasperreports.engine.design.JRDefaultCompilationSourceCode;
import net.sf.jasperreports.engine.design.JRSourceCompileTask;
import scala.Function1;
import scala.collection.immutable.List;
import scala.collection.mutable.ListBuffer;
import scala.collection.mutable.StringBuilder;
import scala.tools.nsc.CompilationUnits.CompilationUnit;
import scala.tools.nsc.CompilerCommand;
import scala.tools.nsc.Global;
import scala.tools.nsc.Properties;
import scala.tools.nsc.Settings;
import scala.tools.nsc.reporters.ConsoleReporter;
import scala.tools.nsc.reporters.Reporter;

/**
 * Calculator compiler that uses scala to compile expressions.
 * 
 * @author Steve Park (slwkf1@users.sourceforge.net)
 *
 */
public class JRScalaCompiler extends JRAbstractJavaCompiler {

	public JRScalaCompiler(JasperReportsContext jasperReportsContext) {
		super(jasperReportsContext, true);   // Scala compiler will only work with saved files at this time
	}

	/**
	 * @deprecated Replaced by {@link #JRScalaCompiler(JasperReportsContext)}.
	 */
	public JRScalaCompiler() {
		super(DefaultJasperReportsContext.getInstance(), true);    // Scala compiler will only work with saved files at this time
	}

	/**
	 * @see net.sf.jasperreports.engine.design.JRAbstractCompiler#checkLanguage(java.lang.String)
	 */
	protected void checkLanguage(String language) throws JRException {
		if (
				!JRReport.LANGUAGE_SCALA.equals(language)
				&& !JRReport.LANGUAGE_JAVA.equals(language)
				)
			{
				throw 
					new JRException(
						"Language \"" + language 
						+ "\" not supported by this report compiler.\n"
						+ "Expecting \"scala\" or \"java\" instead."
						);
			}
	}

	/**
	 * @see net.sf.jasperreports.engine.design.JRAbstractCompiler#compileUnits(net.sf.jasperreports.engine.design.JRCompilationUnit[], java.lang.String, java.io.File)
	 */
	protected String compileUnits(JRCompilationUnit[] units, String classpath,
			File tempDirFile) throws JRException {
		try {
			Function1 error = new StringBuilder();
			Settings compilerSettings = new Settings(error);
			Reporter reporter = new ConsoleReporter(compilerSettings);
			ListBuffer<String> argListBuf = new ListBuffer<String>();
			argListBuf.$plus$eq("-classpath");
			argListBuf.$plus$eq(classpath);
			argListBuf.$plus$eq("-d");
			argListBuf.$plus$eq(tempDirFile.getCanonicalPath());
			java.util.List<File> fileList = new java.util.ArrayList<File>();
			for (int i = 0; i < units.length; i++) {
				argListBuf.$plus$eq(" " + units[i].getSourceFile().getCanonicalPath());
				fileList.add(new File(units[i].getSourceFile().getCanonicalPath()));
			}
			System.out.println(argListBuf.toList().toString());
			CompilerCommand command = new CompilerCommand(argListBuf.toList(), compilerSettings);
			Global global = new Global(compilerSettings, reporter);

			if (command.settings().version().value().equals(Boolean.TRUE)) {
				reporter.info(null, "scala compiler " + Properties.versionString() + " -- " + Properties.copyrightString() , true);
			} else {
				if (command.settings().target().value().equals("msil")) {
					String libPath = System.getProperty("msil.libpath");
					if (libPath != null) {
						List valueList = new ListBuffer().toList();
						valueList.addString(new StringBuilder(command.settings().assemrefs().value() + File.pathSeparator + libPath));
						command.settings().assemrefs().tryToSet(valueList);
					}
				}
				if (reporter.hasErrors()) {
					reporter.flush();
					return null;
				}
				if (command.shouldStopWithInfo()) {
					reporter.info(null, command.getInfoMessage(global), true);
				} else {
					if (command.settings().resident().value().equals(Boolean.TRUE)) {
						resident(global);
					} else if (command.files().isEmpty()) {
						reporter.info(null, command.usageMsg(), true);
						reporter.info(null, global.pluginOptionsHelp(), true);
					} else {
						Global.Run compiler = global.new Run();
						ListBuffer<String> sourceFileList = new ListBuffer<String>();
						for (Iterator<File> iter = fileList.iterator(); iter.hasNext(); ) {
							File jfile = iter.next();
							sourceFileList.$plus$eq(jfile.getCanonicalPath());
						}
						compiler.compile(sourceFileList.toList());
						scala.collection.Iterator<CompilationUnit> compiledUnitIter = compiler.units();
						int inputChar = -1; 
						for (int i = 0; i < units.length; i++) {
							CompilationUnit compiledUnit = compiledUnitIter.next();
							File classFile = new File(tempDirFile + File.separator + compiledUnit.toString().replace(".scala", ".class"));
							FileInputStream in = new FileInputStream(classFile);
							ByteArrayOutputStream out = new ByteArrayOutputStream();
							while((inputChar = in.read()) != -1) {
								out.write(inputChar);
							}
							units[i].setCompileData(out.toByteArray());
							classFile.deleteOnExit();
						}
					}
				}
			}
		} catch (Throwable t) {
			t.printStackTrace();
		} 
		return null;
	}

	/**
	 * @see net.sf.jasperreports.engine.design.JRAbstractCompiler#generateSourceCode(net.sf.jasperreports.engine.design.JRSourceCompileTask)
	 */
	protected JRCompilationSourceCode generateSourceCode(
			JRSourceCompileTask sourceTask) throws JRException {
		return new JRDefaultCompilationSourceCode(JRScalaGenerator.generateClass(sourceTask), null);
	}

	/**
	 * @see net.sf.jasperreports.engine.design.JRAbstractCompiler#getSourceFileName(java.lang.String)
	 */
	protected String getSourceFileName(String unitName) {
		System.out.println(unitName + ".scala");
		return unitName + ".scala";
	}
	
	private void resident(Global global) {
		
	}
}
