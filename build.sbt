name := "Twitter Popularity"
 
version := "1.0"
 
scalaVersion := "2.9.3"
 
resolvers ++= Seq(
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  "DefaultMavenRepository" at "http://repo1.maven.org/maven2/"
)


libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "1.9.1" % "test"  withSources(),
  "org.mockito" % "mockito-all" % "1.9.5" % "test" withSources(),
  "com.typesafe.akka" %  "akka-actor" % "2.0.5"   withSources(),
  "com.typesafe.akka" %  "akka-testkit" % "2.0.5"   withSources(),
  "com.typesafe.akka" % "akka-slf4j" % "2.0.5" withSources(),
  "org.twitter4j" % "twitter4j-stream" % "3.0.3" exclude("commons-logging","commons-logging"),
  "org.slf4j" % "slf4j-api" % "1.7.5",
  "org.slf4j" % "jcl-over-slf4j" % "1.7.5" % "runtime",
  "ch.qos.logback" % "logback-classic" % "1.0.7" % "runtime",
  "com.jsuereth" % "scala-arm_2.9.2" % "1.3"
)

net.virtualvoid.sbt.graph.Plugin.graphSettings