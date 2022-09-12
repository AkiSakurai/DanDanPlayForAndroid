//Custom ijkplayer with more codec, support srt subtitle
//https://github.com/AkiSakurai/ijkplayer
configurations.maybeCreate("default")
fileTree("$projectDir") {
    include("*.aar")
}.forEach {
    artifacts.add("default", it)
}
