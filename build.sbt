// see https://github.com/sbt/sbt-assembly
import AssemblyKeys._ // put this at the top of the file

organization := "com.micronautics"

name := "AkkaFilters"

version := "0.1"

scalaVersion := "2.9.2"

scalaVersion in update := "2.9.2"

scalacOptions ++= Seq("-deprecation", "-unchecked")

scalacOptions in (Compile, doc) <++= baseDirectory.map {
  (bd: File) => Seq[String](
     "-sourcepath", bd.getAbsolutePath,
     "-doc-source-url", "https://github.com/mslinn/AkkaFilters/tree/master/src€{FILE_PATH}.scala"
  )
}

resolvers ++= Seq(
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases"
)

libraryDependencies ++= Seq(
  "org.scalatest"     %% "scalatest"    % "1.8"   % "test" withSources(),
  "com.typesafe.akka" %  "akka-testkit" % "2.0.2" % "test" withSources(),
  "com.typesafe.akka" %  "akka-actor"   % "2.0.2" withSources()
)

seq(assemblySettings: _*)

logLevel := Level.Error

//{System.setProperty("jline.terminal", "none"); seq()} // Windows only

// define the statements initially evaluated when entering 'console', 'console-quick', or 'console-project'
initialCommands := """
  import akka.dispatch._
  import akka.util.Duration
  import akka.util.duration._
"""

// Only show warnings and errors on the screen for compilations.
// This applies to both test:compile and compile and is Info by default
logLevel in compile := Level.Warn

