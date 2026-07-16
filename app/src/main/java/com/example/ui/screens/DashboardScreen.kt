package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.border
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import com.example.data.Student
import com.example.data.SchoolManagerRepository
import com.example.ui.components.BarChart
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import android.content.Intent
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    repository: SchoolManagerRepository,
    onLogout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showExitDialog by remember { mutableStateOf(false) }

    // Intercept back actions for professional exit confirmation
    BackHandler(enabled = true) {
        showExitDialog = true
    }

    // Screen states
    val listState = rememberLazyListState()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
    var studentSortOrder by remember { mutableStateOf(repository.studentSortOrderPreference) } // 0 = Default, 1 = Name A-Z, 3 = Roll No / Admission

    var selectedClass by remember { mutableStateOf(repository.activeClassPreference) }
    var searchQuery by remember { mutableStateOf("") }
    val studentList by repository.getStudentsByClass(selectedClass).collectAsStateWithLifecycle(initialValue = emptyList())
    var classCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    
    val allLocalStudents by repository.getAllStudentsFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val unsyncedTotal by repository.getUnsyncedCountFlow().collectAsStateWithLifecycle(initialValue = 0)
    
    var loadingState by remember { mutableStateOf(false) }
    var loaderMessage by remember { mutableStateOf("") }
    var clockString by remember { mutableStateOf("") }
    var isInitialScreenLoading by remember { mutableStateOf(true) }
    var showNoticeAnnouncementDialog by remember { mutableStateOf(false) }
    var isNoticeDismissed by remember { mutableStateOf(false) }
    val contentAlpha by animateFloatAsState(
        targetValue = if (isInitialScreenLoading) 0f else 1f,
        animationSpec = tween(durationMillis = 400)
    )

    // Suspend-based backend fetcher to read synced data in real-time
    suspend fun performSyncData() {
        // Seed defaults first if local
        repository.seedMockDataIfNeeded()

        // Fetch list in active class sheet to update local cache
        repository.syncStudentData(selectedClass)

        // Fetch aggregate stats for the bar chart
        classCounts = repository.loadClassCounts()
    }

    // Load elements and class counts function
    fun syncData(blocking: Boolean = false) {
        if (blocking) {
            loadingState = true
            loaderMessage = "Synchronizing class records..."
        }
        coroutineScope.launch {
            try {
                performSyncData()
            } finally {
                if (blocking) {
                    loadingState = false
                    loaderMessage = ""
                }
            }
        }
    }

    // Dialog trigger states
    var showFormDialog by remember { mutableStateOf(false) }
    var studentToEdit by remember { mutableStateOf<Student?>(null) }
    var showProfileDialog by remember { mutableStateOf<Student?>(null) }
    var studentToDelete by remember { mutableStateOf<Student?>(null) }

    // Custom Professional dialog states
    var showSuccessDialog by remember { mutableStateOf(false) }
    var successDialogTitle by remember { mutableStateOf("") }
    var successDialogMessage by remember { mutableStateOf("") }

    var showErrorDialog by remember { mutableStateOf(false) }
    var errorDialogTitle by remember { mutableStateOf("") }
    var errorDialogMessage by remember { mutableStateOf("") }

    // Dropdown state
    var classMenuExpanded by remember { mutableStateOf(false) }

    // Dynamic clock ticking (coroutine loop)
    LaunchedEffect(Unit) {
        val clockFormat = SimpleDateFormat("EEEE, d MMM yyyy, HH:mm:ss", Locale.ENGLISH)
        while (true) {
            clockString = clockFormat.format(Date()).uppercase()
            delay(1000)
        }
    }

    // Dynamic connectivity status monitoring for automatic background upload syncs
    var isOnline by remember { mutableStateOf(false) }
    var isAutoSyncing by remember { mutableStateOf(false) }

    val connectivityManager = remember {
        context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    }

    DisposableEffect(connectivityManager) {
        val activeNet = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNet)
        isOnline = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    isOnline = true
                }
            }
            override fun onLost(network: android.net.Network) {
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    isOnline = false
                }
            }
        }

        val request = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            Log.e("Connectivity", "Could not register callback", e)
        }

        onDispose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                // Ignore unregistration errors
            }
        }
    }

    // Auto-sync routine: triggers instantly when internet comes back or when app starts online
    LaunchedEffect(isOnline, unsyncedTotal) {
        if (isOnline && unsyncedTotal > 0 && repository.isCloudMode && !isAutoSyncing) {
            isAutoSyncing = true
            try {
                val syncRes = repository.syncOfflineRecords(selectedClass)
                syncRes.onSuccess { syncedCount ->
                    if (syncedCount > 0) {
                        performSyncData()
                    }
                }.onFailure { err ->
                    Log.e("ConnectivityAutoSync", "Offline automatic sync failed background thread run", err)
                }
            } catch (ex: Exception) {
                Log.e("ConnectivityAutoSync", "Unhandled auto-sync exception loop", ex)
            } finally {
                isAutoSyncing = false
            }
        }
    }



    // Trigger loading on active class switch (Instant local loads, background silent sync!)
    LaunchedEffect(selectedClass) {
        repository.activeClassPreference = selectedClass
        if (isInitialScreenLoading) {
            try {
                // Fetch dynamic notice from Apps Script / Google Sheets
                try {
                    if (repository.isCloudMode) {
                        repository.fetchNoticeSettings()
                    }
                } catch (ne: Exception) {
                    Log.e("InitialLoadNotice", "Failed fetching notice from server", ne)
                } finally {
                    if (repository.isCloudMode && !isNoticeDismissed) {
                        showNoticeAnnouncementDialog = repository.isNoticeEnabled
                    }
                }

                // Phase 1: Seed default mock student lists if offline/fresh database
                repository.seedMockDataIfNeeded()

                // Phase 2: Fetch and sync current selected class sheet securely
                repository.syncStudentData(selectedClass)

                // Phase 3: Query real-time class lists counts for live statistical chart
                classCounts = repository.loadClassCounts()

                // Add small delay to display beautiful high-fidelity circular loader smoothly
                delay(1500)
            } catch (e: Exception) {
                Log.e("InitialLoad", "Failed to cache starting school dashboards info", e)
            } finally {
                isInitialScreenLoading = false
                repository.isDashboardLoadedPreference = true
            }
        } else {
            syncData(blocking = false)
        }
    }

    // Cleaned search filtering and sorting logic
    val filteredStudents = remember(searchQuery, studentList, studentSortOrder) {
        val baseList = if (searchQuery.isBlank()) {
            studentList
        } else {
            val q = searchQuery.trim().lowercase()
            studentList.filter {
                it.stdName.lowercase().contains(q) ||
                it.stdFname.lowercase().contains(q) ||
                it.admNo.contains(q)
            }
        }
        when (studentSortOrder) {
            1 -> baseList.sortedBy { it.stdName.lowercase() }
            3 -> baseList.sortedBy { it.admNo.lowercase() }
            else -> baseList
        }
    }

    // Add / Edit submission handles
    fun handleSave(stud: Student) {
        showFormDialog = false
        studentToEdit = null
        coroutineScope.launch {
            val res = repository.saveStudent(stud)
            res.onSuccess { successMsg ->
                successDialogTitle = if (stud.localId == 0L) "Student Registered!" else "Student Updated!"
                successDialogMessage = successMsg
                showSuccessDialog = true
                // Fetch the newest records in background
                performSyncData()
            }.onFailure { error ->
                errorDialogTitle = "Draft Saved Offline"
                errorDialogMessage = error.message ?: "Your record was saved locally as a draft but could not be uploaded to Google Sheets yet."
                showErrorDialog = true
            }
        }
    }

    // Delete submission handles
    fun handleDeleteConfirm() {
        val stud = studentToDelete ?: return
        studentToDelete = null
        coroutineScope.launch {
            val res = repository.deleteStudent(stud)
            res.onSuccess { successMsg ->
                successDialogTitle = "Student Record Deleted"
                successDialogMessage = successMsg
                showSuccessDialog = true
                // Fetch the newest records in background
                performSyncData()
            }.onFailure { error ->
                errorDialogTitle = "Delete Error"
                errorDialogMessage = error.message ?: "An error occurred while deleting the student record."
                showErrorDialog = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight(),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerTonalElevation = 6.dp,
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
            ) {
                // Header of Drawer with Profile, School Name, and Connection Status
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 26.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Profile circular picture container with glowing primary ring
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                )
                                .padding(3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = "School Academic Emblem",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = repository.username.uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("drawer_profile_name")
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            // Dynamic network connection / status tag
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "SECURE ADMIN",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // School Name Card/Field
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationCity,
                            contentDescription = "School City Location",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = repository.schoolName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("drawer_school_name"),
                            lineHeight = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live synchronization bullet status card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (unsyncedTotal == 0) Color(0xFF4CAF50) else Color(0xFFFF9800))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (unsyncedTotal == 0) "Cloud Data linked & active" else "$unsyncedTotal updates awaiting sync",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (unsyncedTotal == 0) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Drawer items navigation with M3 layout styling
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "MANAGEMENT CONSOLE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        letterSpacing = 1.sp
                    )

                    NavigationDrawerItem(
                        icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home & Analytics", fontWeight = FontWeight.Bold) },
                        selected = true,
                        badge = {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (selectedClass == "Nursury") "Nursery" else selectedClass,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        },
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("drawer_home_item")
                    )

                    if (repository.username.lowercase() == "admin") {
                        NavigationDrawerItem(
                            icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Cloud Setup Settings", fontWeight = FontWeight.Bold) },
                            selected = false,
                            onClick = {
                                coroutineScope.launch {
                                    drawerState.close()
                                    onNavigateToSettings()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("drawer_settings_item")
                        )
                    }

                    NavigationDrawerItem(
                        icon = { Icon(imageVector = Icons.Default.AddCircle, contentDescription = "Enroll", tint = MaterialTheme.colorScheme.primary) },
                        label = { Text("Enroll New Student", fontWeight = FontWeight.Bold) },
                        selected = false,
                        badge = {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "NEW",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        },
                        onClick = {
                            coroutineScope.launch {
                                drawerState.close()
                                studentToEdit = null
                                showFormDialog = true
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("drawer_add_student_item")
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "SUPPORT & COOPERATIVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        letterSpacing = 1.sp
                    )

                    NavigationDrawerItem(
                        icon = { Icon(imageVector = Icons.Default.Chat, contentDescription = "Contact us on WhatsApp", tint = Color(0xFF25D366)) },
                        label = { Text("Contact Us (WhatsApp)", fontWeight = FontWeight.Bold) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                drawerState.close()
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://whatsapp.com/channel/0029VaCK4y0EquiKbkl09S1A"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e("DrawerMenu", "Error opening WhatsApp Link", e)
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("drawer_contact_whatsapp_item")
                    )

                    NavigationDrawerItem(
                        icon = { Icon(imageVector = Icons.Default.Share, contentDescription = "Share application link") },
                        label = { Text("Share Portal App", fontWeight = FontWeight.Bold) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                drawerState.close()
                                try {
                                    val shareIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Check out the School Management System Portal Pro App! Join our official channel for latest updates and continuous support:\n\nhttps://whatsapp.com/channel/0029VaCK4y0EquiKbkl09S1A"
                                        )
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share SMS Portal Pro App Via"))
                                } catch (e: Exception) {
                                    Log.e("DrawerMenu", "Error sharing App", e)
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("drawer_share_app_item")
                    )

                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Polished metric monitoring overview card at bottom of items list
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "PORTAL STATISTICS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Total Students", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${allLocalStudents.size}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Unsynced queue", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (unsyncedTotal == 0) "None (OK)" else "$unsyncedTotal pending",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (unsyncedTotal == 0) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(4.dp))

                    NavigationDrawerItem(
                        icon = { Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error) },
                        label = { Text("Logout Securely", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                drawerState.close()
                                onLogout()
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("drawer_logout_item")
                    )
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = contentAlpha
            }
            .blur(if (isInitialScreenLoading) 24.dp else 0.dp)
    ) {
        Scaffold(
            floatingActionButton = {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showScrollToTop,
                    enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()
                ) {
                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .padding(bottom = 8.dp, end = 4.dp)
                            .size(48.dp)
                            .testTag("scroll_to_top_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Scroll to top of student list"
                        )
                    }
                }
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = innerPadding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    end = innerPadding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    bottom = innerPadding.calculateBottomPadding()
                )
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header panel (Modern and sharp layout)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(
                        top = innerPadding.calculateTopPadding() + 16.dp,
                        bottom = 16.dp
                    )
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("hamburger_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Drawer navigation",
                                tint = Color.White
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = repository.schoolName,
                                style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = clockString.ifEmpty { "LOADING CLOCK..." },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 0.5.sp
                        )
                    }
                    }

                    Row {
                        if (repository.username.lowercase() == "admin") {
                            IconButton(onClick = onNavigateToSettings, modifier = Modifier.testTag("dashboard_settings_button")) {
                                Icon(imageVector = Icons.Default.CloudQueue, contentDescription = "Sheets Syncer Settings", tint = Color.White)
                            }
                        }
                        IconButton(onClick = onLogout, modifier = Modifier.testTag("logout_button")) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout Securely", tint = Color.White)
                        }
                    }
                }
            }

            // Scrollable contents layout
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Connection badge & loading bar
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (repository.isCloudMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                color = if (repository.isCloudMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (repository.isCloudMode) "CLOUD GOOGLE SHEETS ACTIVE" else "OFFLINE LOCAL DATABASE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (repository.isCloudMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            if (loadingState || isAutoSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(
                                    onClick = { syncData(blocking = true) },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("refresh_dashboard_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Sync refresh",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Beautiful High-fidelity Unsynced Local Queue Banner
                        if (unsyncedTotal > 0 && repository.isCloudMode) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("unsynced_warning_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudQueue,
                                            contentDescription = "Offline Records Queue Warning indicator",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "$unsyncedTotal OFFLINE RECORD(S) FOUND",
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            letterSpacing = 1.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Aapki entries local database me save hain. Jab Internet available ho, Cloud par upload karne k liye Sync complete karein.",
                                            style = MaterialTheme.typography.bodySmall,
                                            lineHeight = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            loadingState = true
                                            loaderMessage = "Uploading $unsyncedTotal offline students to Google Sheets..."
                                            coroutineScope.launch {
                                                val syncRes = repository.syncOfflineRecords(selectedClass) { current, total ->
                                                    loaderMessage = "Uploading offline records... ($current of $total)"
                                                }
                                                syncRes.onSuccess { syncedCount ->
                                                    // Refresh newly fetched datasets
                                                    performSyncData()
                                                    successDialogTitle = "Offline Sync Successful!"
                                                    successDialogMessage = "Mubarak ho! $syncedCount offline students records cloud sheet par realtime me send aur link ho chukay hain."
                                                    showSuccessDialog = true
                                                }.onFailure { err ->
                                                    errorDialogTitle = "Offline Sync Failed"
                                                    errorDialogMessage = err.message ?: "Could not complete real-time sync. Check your web app configuration."
                                                    showErrorDialog = true
                                                }
                                                loadingState = false
                                                loaderMessage = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("upload_offline_sync_button")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudUpload,
                                                contentDescription = "Upload icon",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text("SYNC", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Custom Canvas Enrollment Chart Card
                item {
                    Card(
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = "Class distribution chart indicator",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "STUDENT ENROLLMENT STATISTICS",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            BarChart(
                                data = classCounts,
                                barColorStart = MaterialTheme.colorScheme.primary,
                                barColorEnd = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                // Metrics block (aggregated statistics in highly premium scrollable row)
                item {
                    val totalAcademicSum = classCounts.values.sum()
                    val activeClassCount = studentList.size
                    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Card 1: Total Academy
                            PremiumMetricCard(
                                label = "TOTAL ACADEMY",
                                value = "$totalAcademicSum STUDENTS",
                                gradientColors = listOf(Color(0xFF4361EE), Color(0xFF3F37C9)),
                                icon = Icons.Default.School,
                                contentDescription = "Total academy students count"
                            )
                            
                            // Card 2: Current Class Program Count
                            PremiumMetricCard(
                                label = "CLASS STRENGTH",
                                value = "$activeClassCount ENROLLED",
                                gradientColors = listOf(Color(0xFF3B82F6), Color(0xFF10B981)),
                                icon = Icons.Default.Group,
                                contentDescription = "Current class strength counter"
                            )
                            
                            // Card 3: Unsynced drafts queue
                            PremiumMetricCard(
                                label = "OFFLINE DRAFTS",
                                value = if (unsyncedTotal > 0) "$unsyncedTotal PENDING" else "ALL SYNCED 🎉",
                                gradientColors = if (unsyncedTotal > 0) {
                                    listOf(Color(0xFFF59E0B), Color(0xFFEF4444))
                                } else {
                                    listOf(Color(0xFF10B981), Color(0xFF047857))
                                },
                                icon = Icons.Default.CloudQueue,
                                contentDescription = "Offline unsynced student drafts count"
                            )
                        }

                        // Relocated 'ADD NEW STUDENT' button directly below summary cards
                        Button(
                            onClick = {
                                studentToEdit = null
                                showFormDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("add_student_fab"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Enroll new student icon",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ADD NEW STUDENT record",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }

                // Sticky Frosted-Glass Search & Class Selector Tabs
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Search Bar with custom Material styling & Clear Action Button
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { 
                                    Text(
                                        text = "Search by Student Name or Roll...", 
                                        fontSize = 12.sp, 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    ) 
                                },
                                leadingIcon = { 
                                    Icon(
                                        imageVector = Icons.Default.Search, 
                                        contentDescription = "Search", 
                                        tint = MaterialTheme.colorScheme.primary, 
                                        modifier = Modifier.size(20.dp)
                                    ) 
                                },
                                trailingIcon = if (searchQuery.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear, 
                                                contentDescription = "Clear", 
                                                tint = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                } else null,
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("student_search_bar"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            
                            // Interactive Student Sorting Controls (Name list sorting selection row)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "Sort Icon",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "TARTEEB:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = 0.5.sp
                                    )
                                    
                                    val sortOptions = listOf(
                                        "Default" to 0,
                                        "Name (A-Z)" to 1,
                                        "Roll No" to 3
                                    )
                                    
                                    sortOptions.forEach { (optionLabel, optionIdx) ->
                                        val isSel = studentSortOrder == optionIdx
                                        SuggestionChip(
                                            onClick = { 
                                                studentSortOrder = optionIdx 
                                                repository.studentSortOrderPreference = optionIdx
                                            },
                                            label = { Text(optionLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                            colors = SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                labelColor = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            border = SuggestionChipDefaults.suggestionChipBorder(
                                                borderColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                                enabled = true
                                            ),
                                            modifier = Modifier.height(28.dp).testTag("sort_chip_$optionIdx")
                                        )
                                    }
                                }
                            }
                            
                            // Sliding sideways class-tabs in the same frosted panel
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val classes = listOf("Nursury", "Prep", "Class 1", "Class 2", "Class 3", "Class 4", "Class 5")
                                classes.forEach { cls ->
                                    val isActive = selectedClass == cls
                                    val backgroundColor by animateColorAsState(
                                        targetValue = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        animationSpec = tween(durationMillis = 200)
                                    )
                                    val contentColor by animateColorAsState(
                                        targetValue = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        animationSpec = tween(durationMillis = 200)
                                    )
                                    val borderAlpha by animateFloatAsState(
                                        targetValue = if (isActive) 0f else 0.15f
                                    )
                                    
                                    val classStudentCount = classCounts[cls] ?: 0
                                    
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = backgroundColor,
                                        border = if (!isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = borderAlpha)) else null,
                                        modifier = Modifier
                                            .clickable { selectedClass = cls }
                                            .testTag("class_tab_$cls")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (cls == "Nursury") "Nursery" else cls,
                                                fontSize = 12.sp,
                                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                                color = contentColor
                                            )
                                            // Dynamic tiny badge showing class strength
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isActive) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                modifier = Modifier.size(18.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = classStudentCount.toString(),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = contentColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // List header count bar
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "REGISTERED STUDENTS (${filteredStudents.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                        
                        if (isAutoSyncing || loadingState) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Updating list...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Empty state or staggered items render mapping
                if (filteredStudents.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth(0.95f)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                    .padding(28.dp)
                            ) {
                                // Dynamic layered graphics representing an illustration
                                Box(
                                    modifier = Modifier.size(90.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 4.dp,
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (searchQuery.isNotBlank()) Icons.Default.Search else Icons.Default.School,
                                                contentDescription = "Empty illustration",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Text(
                                    text = if (searchQuery.isNotBlank()) "Koyi Record Nahi Mila" else "Class Khali Hai",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                Text(
                                    text = if (searchQuery.isNotBlank()) {
                                        "Sahi spelling ya kisi aur class me student search karkay dekhain."
                                    } else {
                                        "Is class me koi student registered nahi hai. Naye records submit karne k liye niche click kijiye."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                                
                                if (searchQuery.isBlank()) {
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(
                                        onClick = {
                                            studentToEdit = null
                                            showFormDialog = true
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                        modifier = Modifier.testTag("empty_state_add_student_button")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Add Student icon",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "ENROLL FIRST STUDENT",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .testTag("students_table_card")
                        ) {
                            val tableScrollState = rememberScrollState()
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(tableScrollState)
                            ) {
                                TableHeaderRow()
                                
                                filteredStudents.forEachIndexed { index, student ->
                                    TableDataRow(
                                        student = student,
                                        index = index,
                                        onClick = { showProfileDialog = student },
                                        onEdit = {
                                            studentToEdit = student
                                            showFormDialog = true
                                        },
                                        onDelete = { studentToDelete = student }
                                    )
                                    if (index < filteredStudents.size - 1) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                            thickness = 1.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    // Modal forms Orchestrations
    if (showFormDialog) {
        StudentFormDialog(
            studentToEdit = studentToEdit,
            currentClass = selectedClass,
            onDismiss = {
                showFormDialog = false
                studentToEdit = null
            },
            onSave = ::handleSave
        )
    }

    if (showProfileDialog != null) {
        StudentProfileDialog(
            student = showProfileDialog!!,
            onDismiss = { showProfileDialog = null }
        )
    }

    if (studentToDelete != null) {
        AlertDialog(
            onDismissRequest = { studentToDelete = null },
            icon = { Icon(imageVector = Icons.Default.Warning, contentDescription = "Permanent Delete", tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Student Record?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete ${studentToDelete?.stdName}'s record? This cannot be undone.", fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = ::handleDeleteConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("DELETE RECORD")
                }
            },
            dismissButton = {
                TextButton(onClick = { studentToDelete = null }, modifier = Modifier.testTag("cancel_delete_button")) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("delete_student_alert_dialog")
        )
    }

    // Professional Exit Confirmation Dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Confirm exit warning icon",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Exit SMS PRO Portal?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to exit the application? Make sure your spreadsheet synchronizations are complete.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        (context as? android.app.Activity)?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("dialog_confirm_exit_button")
                ) {
                    Text("Exit Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showExitDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("dialog_dismiss_exit_button")
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            },
            modifier = Modifier.testTag("professional_exit_alert_dialog")
        )
    }

    // High-Fidelity Beautiful Full-Screen Startup Loader Overlay
    if (isInitialScreenLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
                .clickable(enabled = true, onClickLabel = null, role = null, onClick = {}) // Consume pointer events to prevent clicking background elements
                .testTag("initial_dashboard_loader"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                // Circular Academic Emblem container with glowing halo details
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = "Analytics Emblem",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(54.dp)
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Premium Indeterminate Circular Loader
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    strokeWidth = 4.5.dp,
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("initial_loader_circular_indicator")
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Typography branding details
                Text(
                    text = repository.schoolName.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.8.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "SMS Portal Pro",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Elegant progress status card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.widthIn(max = 300.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = "Cloud Queue Syncing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Loading school statistics...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Professional Full-screen Backdrop Progress Loader
    if (loadingState) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {}, // Lock dismissal to ensure critical sync transactions are not aborted
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.52f)) // High-fidelity dark ambient overlay
                    .testTag("processing_loader_overlay"),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .width(300.dp)
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp,
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("processing_loader_indicator")
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "REAL-TIME PORTAL",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (loaderMessage.isNotEmpty()) loaderMessage else "Synchronizing datasets in real-time...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.testTag("processing_loader_text")
                        )
                    }
                }
            }
        }
    }

    // Professional Success Notification Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            },
            title = {
                Text(
                    text = successDialogTitle,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = successDialogMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSuccessDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .testTag("success_dialog_confirm_button")
                ) {
                    Text("Great, Thank You", fontWeight = FontWeight.Bold)
                }
            },
            modifier = Modifier.testTag("professional_success_alert_dialog")
        )
    }

    // Professional Error Notification Dialog
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error icon",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                }
            },
            title = {
                Text(
                    text = errorDialogTitle,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = errorDialogMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { showErrorDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .testTag("error_dialog_confirm_button")
                ) {
                    Text("Acknowledge", fontWeight = FontWeight.Bold)
                }
            },
            modifier = Modifier.testTag("professional_error_alert_dialog")
        )
    }

    // Dynamic Announcement / Alert Notice Dialog
    if (showNoticeAnnouncementDialog && repository.isNoticeEnabled) {
        val noticeUrl = repository.noticeActionUrl
        AlertDialog(
            onDismissRequest = { 
                showNoticeAnnouncementDialog = false 
                isNoticeDismissed = true
            },
            icon = {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Announcement Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            },
            title = {
                Text(
                    text = repository.noticeTitle,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = repository.noticeMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { 
                        showNoticeAnnouncementDialog = false 
                        isNoticeDismissed = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .testTag("notice_dialog_dismiss_button")
                ) {
                    Text("Dismiss", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNoticeAnnouncementDialog = false
                        isNoticeDismissed = true
                        if (noticeUrl.isNotBlank()) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(noticeUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Log.e("DashboardScreen", "Failed to compile/redirect notice URL", e)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .testTag("notice_dialog_join_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Go Link",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("OK / JOIN NOW", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            },
            modifier = Modifier.testTag("custom_notice_alert_dialog")
        )
    }
}

@Composable
private fun TableHeaderCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    alignment: Alignment = Alignment.CenterStart
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = alignment
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TableHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TableHeaderCell(text = "#", width = 50.dp, alignment = Alignment.Center)
        TableHeaderCell(text = "STATUS", width = 75.dp, alignment = Alignment.Center)
        TableHeaderCell(text = "NAME", width = 180.dp)
        TableHeaderCell(text = "ADM NO", width = 110.dp)
        TableHeaderCell(text = "FATHER'S NAME", width = 160.dp)
        TableHeaderCell(text = "DOB", width = 110.dp)
        TableHeaderCell(text = "GENDER", width = 85.dp, alignment = Alignment.Center)
        TableHeaderCell(text = "ADM DATE", width = 115.dp)
        TableHeaderCell(text = "ACTIONS", width = 140.dp, alignment = Alignment.Center)
    }
}

@Composable
private fun TableDataRow(
    student: Student,
    index: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isMale = student.gender.uppercase() == "MALE"
    val rowBgColor = if (index % 2 == 0) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBgColor)
            .clickable(onClick = onClick)
            .testTag("student_row_${student.admNo}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Index Column
        Box(
            modifier = Modifier
                .width(50.dp)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        
        // 2. Status Badge Column
        Box(
            modifier = Modifier
                .width(75.dp)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (student.rowId == null) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Unsynced Offline Draft",
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier
                        .size(16.dp)
                        .testTag("student_offline_badge_${student.admNo}")
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Synced Live to cloud",
                    tint = Color(0xFF22C55E),
                    modifier = Modifier
                        .size(16.dp)
                        .testTag("student_live_badge_${student.admNo}")
                )
            }
        }
        
        // 3. Name Column
        Row(
            modifier = Modifier
                .width(180.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val avatarCircleColor = if (isMale) Color(0xFFE0F2FE) else Color(0xFFFCE7F3)
            val avatarTextColor = if (isMale) Color(0xFF0284C7) else Color(0xFFDB2777)
            
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(avatarCircleColor),
                contentAlignment = Alignment.Center
            ) {
                val splitName = student.stdName.split(" ")
                val initials = if (splitName.size >= 2) {
                    "${splitName[0].take(1)}${splitName[1].take(1)}"
                } else {
                    student.stdName.take(2)
                }
                Text(
                    text = initials.uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = avatarTextColor
                )
            }
            
            Spacer(modifier = Modifier.width(10.dp))
            
            Text(
                text = student.stdName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // 4. Admission Number Column
        Box(
            modifier = Modifier
                .width(110.dp)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                text = "#${student.admNo}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // 5. Father's Name Column
        Box(
            modifier = Modifier
                .width(160.dp)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                text = student.stdFname,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // 6. DOB Column
        Box(
            modifier = Modifier
                .width(110.dp)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                text = student.dob,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // 7. Gender Column
        Box(
            modifier = Modifier
                .width(85.dp)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val genderColor = if (isMale) Color(0xFF0369A1) else Color(0xFFBE185D)
            val genderBg = if (isMale) Color(0xFFF0F9FF) else Color(0xFFFDF2F8)
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(genderBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = student.gender.uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = genderColor
                )
            }
        }
        
        // 8. Admission Date Column
        Box(
            modifier = Modifier
                .width(115.dp)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                text = student.admDate,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // 9. Actions Column
        Row(
            modifier = Modifier
                .width(140.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("action_view_${student.admNo}")
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "Details",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("action_edit_${student.admNo}")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit record",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("action_delete_${student.admNo}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete permanently",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun PremiumMetricCard(
    label: String,
    value: String,
    gradientColors: List<Color>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .width(170.dp)
            .height(105.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = Brush.verticalGradient(colors = gradientColors))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                .padding(14.dp)
        ) {
            // Shiny decorative glass circle in top right corner
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = 0.8f),
                    letterSpacing = 1.sp
                )
                
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
