package launcher

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "${AppInfo.NAME} - ${AppInfo.NOTICE}",
        state = rememberWindowState(width = 1050.dp, height = 720.dp),
    ) {
        App()
    }
}
