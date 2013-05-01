name := "Twitter Popularity"
 
version := "1.0"
 
scalaVersion := "2.9.3"
 
resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "1.9.1" % "test"  withSources(),
  "org.mockito" % "mockito-all" % "1.9.5" % "test" withSources(),
  "com.typesafe.akka" %  "akka-actor" % "2.0.5"   withSources(),
  "com.typesafe.akka" %  "akka-testkit" % "2.0.5"   withSources(),
  "com.typesafe.akka" % "akka-kernel" % "2.0.5" withSources(),
  "com.typesafe.akka" % "akka-slf4j" % "2.0.5" withSources(),
  "ch.qos.logback" % "logback-classic" % "1.0.7" % "runtime",
  "org.apache.httpcomponents" % "httpclient" % "4.2.3",
  "org.apache.httpcomponents" % "httpmime" % "4.2.3",
  "org.twitter4j" % "twitter4j-stream" % "3.0.3"
)
