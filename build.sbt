import scalapb.compiler.Version.{grpcJavaVersion, scalapbVersion, protobufVersion}

val unusedWarnings = (
  "-Ywarn-unused" ::
  Nil
)

def Scala212 = "2.13.4"

val commonSettings: Seq[Def.Setting[_]] = Seq[Def.Setting[_]](
  fork in Test := true,
  scalaVersion := Scala212,
  crossScalaVersions := List(Scala212), // TODO add Scala 2.13
  libraryDependencies += "io.grpc" % "grpc-netty" % grpcJavaVersion,
  libraryDependencies += "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion % "protobuf"
)

lazy val grpcScalaSample = project.in(file("grpc-scala")).settings(
  commonSettings,
  libraryDependencies += "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapbVersion,
  PB.targets in Compile := Seq(scalapb.gen() -> (sourceManaged in Compile).value),
  unmanagedResourceDirectories in Compile += (baseDirectory in LocalRootProject).value / "grpc-java/examples/src/main/resources",
  PB.protoSources in Compile += (baseDirectory in LocalRootProject).value / "grpc-java/examples/src/main/proto",
  scalacOptions ++= (
    "-deprecation" ::
    "-unchecked" ::
    "-Xlint" ::
    "-language:existentials" ::
    "-language:higherKinds" ::
    "-language:implicitConversions" ::
    Nil
  ) ::: unusedWarnings,
  Seq(Compile, Test).flatMap(c =>
    scalacOptions in (c, console) --= unusedWarnings
  )
)
