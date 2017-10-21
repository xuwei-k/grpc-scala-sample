import com.trueaccord.scalapb.compiler.Version.{grpcJavaVersion, scalapbVersion, protobufVersion}

val unusedWarnings = (
  "-Ywarn-unused" ::
  "-Ywarn-unused-import" ::
  Nil
)

lazy val root = project.in(file(".")).aggregate(
  grpcJavaSample, grpcScalaSample
)

val commonSettings: Seq[Def.Setting[_]] = Seq[Def.Setting[_]](
  fork in Test := true,
  scalaVersion := "2.12.4",
  libraryDependencies += "io.grpc" % "grpc-netty" % grpcJavaVersion,
  libraryDependencies += "com.trueaccord.scalapb" %% "scalapb-runtime" % scalapbVersion % "protobuf"
)

lazy val grpcScalaSample = project.in(file("grpc-scala")).settings(
  commonSettings,
  libraryDependencies += "com.trueaccord.scalapb" %% "scalapb-runtime-grpc" % scalapbVersion,
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
    "-Yno-adapted-args" ::
    Nil
  ) ::: unusedWarnings,
  Seq(Compile, Test).flatMap(c =>
    scalacOptions in (c, console) --= unusedWarnings
  )
)

val grpcArtifactId = "protoc-gen-grpc-java"

lazy val grpcExeFileName = {
  val os = if (scala.util.Properties.isMac){
    "osx-x86_64"
  } else if (scala.util.Properties.isWin){
    "windows-x86_64"
  } else {
    "linux-x86_64"
  }
  s"${grpcArtifactId}-${grpcJavaVersion}-${os}.exe"
}

lazy val grpcExeUrl =
  url(s"https://repo1.maven.org/maven2/io/grpc/${grpcArtifactId}/${grpcJavaVersion}/${grpcExeFileName}")

val grpcExePath = SettingKey[xsbti.api.Lazy[File]]("grpcExePath")

lazy val grpcJavaSample = project.in(file("grpc-java/examples")).settings(
  commonSettings,
  PB.targets in Compile := Seq(PB.gens.java(protobufVersion) -> (sourceManaged in Compile).value),
  PB.protocOptions in Compile ++= Seq(
    s"--plugin=protoc-gen-java_rpc=${grpcExePath.value.get}",
    s"--java_rpc_out=${((sourceManaged in Compile).value).getAbsolutePath}"
  ),
  grpcExePath := xsbti.api.SafeLazyProxy {
    val exe = (baseDirectory in LocalRootProject).value / ".bin" / grpcExeFileName
    if (!exe.isFile) {
      println("grpc protoc plugin (for Java) does not exist. Downloading.")
      sbt.io.Using.urlInputStream(grpcExeUrl) { inputStream =>
        IO.transfer(inputStream, exe)
      }
      exe.setExecutable(true)
    }
    exe
  },
  PB.protoSources in Compile += baseDirectory.value / "src/main/proto",
  // https://github.com/grpc/grpc-java/blob/v1.5.0/examples/build.gradle#L28-L35
  libraryDependencies += "io.grpc" % "grpc-protobuf" % grpcJavaVersion,
  libraryDependencies += "io.grpc" % "grpc-stub" % grpcJavaVersion,
  libraryDependencies += "io.grpc" % "grpc-testing" % grpcJavaVersion % "test",
  libraryDependencies += "org.mockito" % "mockito-core" % "1.9.5" % "test",
  libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test",
  autoScalaLibrary := false
)
