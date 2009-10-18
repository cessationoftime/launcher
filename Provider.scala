package xsbt.boot

import Pre._
import java.io.{File, FileFilter}
import java.net.{URL, URLClassLoader}

trait Provider extends NotNull
{
	def configuration: UpdateConfiguration
	def baseDirectories: List[File]
	def testLoadClasses: List[String]
	def target: UpdateTarget
	def failLabel: String
	def parentLoader: ClassLoader

	val (jars, loader) =
	{
		val (existingJars, existingLoader) = createLoader
		if(Check.needsUpdate(existingLoader, testLoadClasses))
		{
			( new Update(configuration) )(target)
			val (newJars, newLoader) = createLoader
			Check.failIfMissing(newLoader, testLoadClasses, failLabel)
			(newJars, newLoader)
		}
		else
			(existingJars, existingLoader)
	}
	def createLoader =
	{
		 val jars = GetJars(baseDirectories)
		(jars, new URLClassLoader(jars.map(_.toURI.toURL), parentLoader) )
	}
}

object GetJars
{
	def apply(directories: List[File]): Array[File] = toArray(directories.flatMap(directory => wrapNull(directory.listFiles(JarFilter))))
	def wrapNull(a: Array[File]): Array[File] = if(a == null) Array() else a

	object JarFilter extends FileFilter
	{
		def accept(file: File) = !file.isDirectory && file.getName.endsWith(".jar")
	}
}
object Check
{
	def failIfMissing(loader: ClassLoader, classes: Iterable[String], label: String) =
		checkTarget(loader, classes, (), missing => error("Could not retrieve " + label + ": missing " + missing.mkString(", ")))
	def needsUpdate(loader: ClassLoader, classes: Iterable[String]) = checkTarget(loader, classes, false, x => true)
	def checkTarget[T](loader: ClassLoader, classes: Iterable[String], ifSuccess: => T, ifFailure: Iterable[String] => T): T =
	{
		def classMissing(c: String) = try { Class.forName(c, false, loader); false } catch { case e: ClassNotFoundException => true }
		val missing = classes.toList.filter(classMissing)
		if(missing.isEmpty) ifSuccess else ifFailure(missing)
	}
}