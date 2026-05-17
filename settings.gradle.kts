rootProject.name = "ARESLib-Kotlin"

include("core")
include("ftc-hardware")
include("simulator")

include("FtcRobotController")
project(":FtcRobotController").projectDir = file("ftc-app/FtcRobotController")

include("TeamCode")
project(":TeamCode").projectDir = file("ftc-app/TeamCode")

