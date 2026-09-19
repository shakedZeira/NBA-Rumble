package com.nbarumble.game

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import com.nbarumble.game.data.repo.AuthRepository
import com.nbarumble.game.data.repo.NbaPlayerRepository
import com.nbarumble.game.data.repo.RoomRepository

class NbaRumbleApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Manual dependency container (keeps the project lean — no DI framework
 * needed for a 1v1 game). Instances live for the whole application.
 */
class AppContainer(context: Context) {

    private val firebaseDatabase: FirebaseDatabase by lazy {
        FirebaseApp.initializeApp(context)
        FirebaseDatabase.getInstance()
    }

    val authRepository: AuthRepository = AuthRepository()
    val nbaPlayerRepository: NbaPlayerRepository = NbaPlayerRepository(context)
    val roomRepository: RoomRepository by lazy { RoomRepository(firebaseDatabase, nbaPlayerRepository) }
}