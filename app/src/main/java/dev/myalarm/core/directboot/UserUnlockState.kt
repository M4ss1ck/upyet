package dev.myalarm.core.directboot

import android.content.Context
import android.os.UserManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Every alarm-path caller must check this before touching credential-protected Room or DataStore. */
interface UserUnlockState {
    fun isUserUnlocked(): Boolean
}

@Singleton
class AndroidUserUnlockState @Inject constructor(@ApplicationContext private val context: Context) : UserUnlockState {
    override fun isUserUnlocked(): Boolean = context.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
}
