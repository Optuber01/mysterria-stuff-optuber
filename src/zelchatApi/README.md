# ZelChat compile-only contract

These minimal interfaces mirror only the methods used from the ZelChat build deployed on
Mysterria (`2.0.0-pre-28`, SHA-256
`DC4D8E6854570F3B8B9259DCA1FB50613E1DF6B9D19499CEAC9DCF93D96CB3E7`). They are compiled
only when the proprietary deployed API jar is unavailable and are never included in the
MysterriaStuff jar.

To verify against the deployed binary directly:

```powershell
.\gradlew.bat clean build -PzelchatJar="C:\path\to\ZelChat-2.0.0-pre-28.jar"
```

The build fails if any `it.pino.zelchat.api` contract class is accidentally bundled.
