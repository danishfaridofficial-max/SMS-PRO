package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import com.example.data.LocalDatabase
import com.example.data.SchoolManagerRepository
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme

enum class Screen {
    SPLASH,
    LOGIN,
    DASHBOARD,
    SETTINGS
}

class MainActivity : ComponentActivity() {
    private lateinit var database: LocalDatabase
    private lateinit var repository: SchoolManagerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = LocalDatabase.getDatabase(this)
        repository = SchoolManagerRepository(this, database.studentDao())

        setContent {
            var themePref by remember { mutableStateOf(repository.themePreference) }
            val coroutineScope = rememberCoroutineScope()

            MyApplicationTheme(themePreference = themePref) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentScreen by remember {
                        mutableStateOf(Screen.SPLASH)
                    }
                    var previousScreen by remember { mutableStateOf(Screen.LOGIN) }

                    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                        when (screen) {
                            Screen.SPLASH -> {
                                SplashScreen(
                                    repository = repository,
                                    onSplashFinished = { target ->
                                        currentScreen = target
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Screen.LOGIN -> {
                                LoginScreen(
                                    repository = repository,
                                    onLoginSuccess = {
                                        currentScreen = Screen.DASHBOARD
                                    },
                                    onNavigateToSettings = {
                                        previousScreen = Screen.LOGIN
                                        currentScreen = Screen.SETTINGS
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Screen.DASHBOARD -> {
                                DashboardScreen(
                                    repository = repository,
                                    onLogout = {
                                        coroutineScope.launch {
                                            repository.logout()
                                            currentScreen = Screen.LOGIN
                                        }
                                    },
                                    onNavigateToSettings = {
                                        previousScreen = Screen.DASHBOARD
                                        currentScreen = Screen.SETTINGS
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Screen.SETTINGS -> {
                                SettingsScreen(
                                    repository = repository,
                                    onBack = {
                                        currentScreen = previousScreen
                                    },
                                    onThemeChanged = { selectedTheme ->
                                        themePref = selectedTheme
                                    },
                                    onTestConnection = { success, msg ->
                                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
