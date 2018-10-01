name := "LibHublab"

version := "0.1"

scalaVersion := "2.12.7"

libraryDependencies += "org.java-websocket" % "Java-WebSocket" % "1.3.9"
libraryDependencies += "com.lihaoyi" %% "ujson" % "0.6.6"

resolvers += Resolver.bintrayRepo("julien-lafont", "maven")

libraryDependencies ++= Seq(
    "io.protoless" %% "protoless-core" % "0.0.7",
    "io.protoless" %% "protoless-generic" % "0.0.7"
)