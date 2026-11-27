package hu.rmz.prefiscore

import android.app.Application
import hu.rmz.prefiscore.data.GameRepository
import hu.rmz.prefiscore.data.Settings
import hu.rmz.prefiscore.ui.AppState

class PrefiApp : Application() {
    val state: AppState by lazy { AppState(GameRepository(filesDir), Settings(this)) }
}
