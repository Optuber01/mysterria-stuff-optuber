# ZelChat public API dependency

The build compiles against the public [ZelChat API](https://github.com/ZelChat/zelchat-api)
source at commit `b32f3d240f1f8eb7216aa53556e01324d17372f6`. The upstream Maven coordinates advertised
by the project are not currently resolvable, so Gradle downloads that immutable GitHub
source archive into `build/generated/sources/zelchatApi` and verifies its SHA-256
(`27bf33f6439e6f074cac9ce56f49351cf7cecbedd965dc6873a52c45314e063c`). No ZelChat API
classes are bundled in the MysterriaStuff jar.

The local `src/zelchatApi/java` directory is intentionally absent: MysterriaStuff does not
maintain a handwritten or reverse-engineered ZelChat API contract.

The deployed binary can additionally be supplied as a compatibility check:

```powershell
.\gradlew.bat clean build -PzelchatJar="C:\path\to\ZelChat-2.0.0-pre-28.jar"
```

The build fails if any `it.pino.zelchat.api` contract class is accidentally bundled.
