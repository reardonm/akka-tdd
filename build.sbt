name := "Akka TDD"
 
version := "1.0"
 
scalaVersion := "2.9.2"
 
resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "1.9.1" % "test"  withSources(),
  "com.typesafe.akka" %  "akka-actor" % "2.0.5"   withSources(),
  "com.typesafe.akka" %  "akka-testkit" % "2.0.5"   withSources()
)
