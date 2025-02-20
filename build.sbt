
scalaVersion := "3.6.2"
name := "cogent"
organization := "org.norvell"
version := "1.1"

libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "2.0.0"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.9" // ???
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.9" % "test"
Test/logBuffered := false

// Most moderately interesting Scala projects don't make use of the very simple
// build file style (called "bare style") used in this build.sbt file. Most
// intermediate Scala projects make use of so-called "multi-project" builds. A
// multi-project build makes it possible to have different folders which sbt can
// be configured differently for. That is, you may wish to have different
// dependencies or different testing frameworks defined for different parts of
// your codebase. Multi-project builds make this possible.

// Here's a quick glimpse of what a multi-project build looks like for this
// build, with only one "subproject" defined, called `root`:

// lazy val root = (project in file(".")).
//   settings(
//     inThisBuild(List(
//       organization := "ch.epfl.scala",
//       scalaVersion := "2.13.3"
//     )),
//     name := "hello-world"
//   )

// To learn more about multi-project builds, head over to the official sbt
// documentation at http://www.scala-sbt.org/documentation.html

// Not sure what this does see https://www.scala-sbt.org/1.x/docs/sbt-server.html
semanticdbEnabled := true 