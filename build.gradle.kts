buildTypes {
    release {
        isMinifyEnabled = false
        signingConfig = signingConfigs.getByName("debug") // <--- இதை இங்க கொண்டு வாங்க
    }
}
