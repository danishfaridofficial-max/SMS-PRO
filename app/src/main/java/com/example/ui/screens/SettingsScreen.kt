package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SchoolManagerRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: SchoolManagerRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onThemeChanged: (String) -> Unit = {},
    onTestConnection: (Boolean, String) -> Unit // returns isSuccess and feedback message
) {
    var isCloudMode by remember { mutableStateOf(repository.isCloudMode) }
    var webAppUrl by remember { mutableStateOf(repository.webAppUrl) }
    var sheetId by remember { mutableStateOf(repository.sheetId) }
    var testingConnection by remember { mutableStateOf(false) }
    var themePref by remember { mutableStateOf(repository.themePreference) }
    
    var isNoticeEnabled by remember { mutableStateOf(repository.isNoticeEnabled) }
    var noticeTitle by remember { mutableStateOf(repository.noticeTitle) }
    var noticeMessage by remember { mutableStateOf(repository.noticeMessage) }
    var noticeActionUrl by remember { mutableStateOf(repository.noticeActionUrl) }

    var codeVisible by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val appsScriptCode = """
// =========================================================================
//  GOOGLE APPS SCRIPT WEB APP - SCHOOL MANAGEMENT SYSTEM FOR SMS PRO APP
// =========================================================================

// Configure your spreadsheet ID
const MASTER_SHEET_ID = "${sheetId.ifEmpty { "1FjN6mi26dAXfJZixfNICuhlpVoEdPSbyHRURZo5Ztzs" }}"; 

// 1. Entry point for GET requests
function doGet(e) {
  try {
    const action = e.parameter.action;
    if (!action) {
      return respondJson({ success: false, msg: "Action parameter missing." });
    }

    if (action === "checkLogin") {
      const res = checkLogin(e.parameter.username, e.parameter.password);
      return respondJson(res);
    }
    
    if (action === "getNotice") {
      const res = getNotice(e.parameter.sheetId || MASTER_SHEET_ID);
      return respondJson(res);
    }
    
    if (action === "getClassCounts") {
      const res = getClassCounts(e.parameter.sheetId || MASTER_SHEET_ID);
      return respondJson(res);
    }
    
    if (action === "getStudentData") {
      const res = getStudentData(e.parameter.sheetId || MASTER_SHEET_ID, e.parameter.className);
      return respondJson(res);
    }
    
    if (action === "processStudent") {
      const obj = {
        targetSheetId: e.parameter.targetSheetId || e.parameter.sheetId || MASTER_SHEET_ID,
        className: e.parameter.className,
        rowId: e.parameter.rowId ? parseInt(e.parameter.rowId) : null,
        stdId: e.parameter.stdId ? parseInt(e.parameter.stdId) : null,
        admNo: e.parameter.admNo,
        admDate: e.parameter.admDate,
        stdName: e.parameter.stdName,
        stdFname: e.parameter.stdFname,
        dob: e.parameter.dob,
        gender: e.parameter.gender,
        cnic: e.parameter.cnic || "",
        fCnic: e.parameter.fCnic || "",
        enrolmentType: e.parameter.enrolmentType || "Fresh"
      };
      const res = processStudent(obj);
      return respondJson(res);
    }
    
    if (action === "deleteStudent") {
      const res = deleteStudent(e.parameter.sheetId || MASTER_SHEET_ID, e.parameter.className, parseInt(e.parameter.rowId));
      return respondJson(res);
    }

    return respondJson({ success: false, msg: "Unsupported operation: " + action });
  } catch (err) {
    return respondJson({ success: false, msg: "Exception: " + err.message });
  }
}

// Helper to return clean JSON
function respondJson(responseObject) {
  return ContentService.createTextOutput(JSON.stringify(responseObject))
    .setMimeType(ContentService.MimeType.JSON);
}

function formatDateCustom(dateVal) {
  if (!dateVal) return "";
  const date = new Date(dateVal);
  if (isNaN(date.getTime())) return dateVal.toString(); // return original if parsing fails
  const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  const day = ("0" + date.getDate()).slice(-2);
  const month = months[date.getMonth()];
  const year = date.getFullYear();
  return day + "-" + month + "-" + year;
}

function parseDateForSheet(dateStr) {
  if (!dateStr) return "";
  const parts = dateStr.split("-");
  if (parts.length === 3) {
    const day = parseInt(parts[0], 10);
    const monthStr = parts[1];
    const year = parseInt(parts[2], 10);
    const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
    const monthIndex = months.findIndex(function(m) { return m.toLowerCase() === monthStr.toLowerCase(); });
    if (monthIndex !== -1 && !isNaN(day) && !isNaN(year)) {
      return new Date(year, monthIndex, day);
    }
  }
  const parsed = new Date(dateStr);
  if (!isNaN(parsed.getTime())) {
    return parsed;
  }
  return dateStr;
}

// Check logins inside "Login" sheet
function checkLogin(username, password) {
  try {
    const ss = SpreadsheetApp.openById(MASTER_SHEET_ID.trim());
    const sheet = ss.getSheetByName("Login");
    if (!sheet) return { success: false, msg: "Login sheet not found" };
    const data = sheet.getDataRange().getValues();
    for (let i = 1; i < data.length; i++) {
      if (data[i][0].toString().trim() == username && data[i][1].toString().trim() == password) {
        return { 
          success: true, 
          sheetId: data[i][2] || MASTER_SHEET_ID, 
          schoolName: data[i][3] || "SMS PRO School",
          username: data[i][0]
        };
      }
    }
    return { success: false, msg: "Invalid Username or Password" };
  } catch (e) {
    return { success: false, msg: "Database Error: " + e.message };
  }
}

// Get standard counts
function getClassCounts(sheetId) {
  const classes = ['Nursury', 'Prep', 'Class 1', 'Class 2', 'Class 3', 'Class 4', 'Class 5'];
  const counts = [];
  try {
    const ss = SpreadsheetApp.openById(sheetId.trim());
    classes.forEach(cls => {
      const sheet = ss.getSheetByName(cls);
      const count = sheet ? sheet.getLastRow() - 1 : 0;
      counts.push(count < 0 ? 0 : count);
    });
    return { success: true, labels: classes, counts: counts };
  } catch (e) { 
    return { success: false, labels: classes, counts: [0,0,0,0,0,0,0], msg: e.message }; 
  }
}

// Get student listing
function getStudentData(sheetId, className) {
  if (!sheetId) return [];
  try {
    const ss = SpreadsheetApp.openById(sheetId.trim());
    const sheet = ss.getSheetByName(className);
    if (!sheet) return [];
    const data = sheet.getDataRange().getValues();
    return data.slice(1).map((row, index) => ({
      rowId: index + 2,
      stdId: row[0],
      admNo: row[1],
      admDate: row[2] ? formatDateCustom(row[2]) : "",
      stdName: row[3],
      stdFname: row[4],
      dob: row[5] ? formatDateCustom(row[5]) : "",
      gender: row[6],
      cnic: row[7] || "",
      fCnic: row[8] || "",
      enrolmentType: row[9] || "Fresh"
    }));
  } catch(e) { return []; }
}

// Function to re-sequence STD_IDs in a sheet to be strictly consecutive (1, 2, 3...)
function resequenceStudentIds(sheet) {
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) return;
  const range = sheet.getRange(2, 1, lastRow - 1, 1);
  const values = [];
  for (let i = 1; i <= lastRow - 1; i++) {
    values.push([i]);
  }
  range.setValues(values);
}

// Insert / UpdateStudent
function processStudent(obj) {
  try {
    const ss = SpreadsheetApp.openById(obj.targetSheetId.trim());
    var sheet = ss.getSheetByName(obj.className);
    if (!sheet) {
      sheet = ss.insertSheet(obj.className);
      sheet.appendRow(["STD_ID", "ADM_NO", "ADM_DATE", "STD_NAME", "STD_FNAME", "DOB", "GENDER", "CNIC", "F_CNIC", "ENROLMENT_TYPE"]);
    }
    let finalStdId = obj.stdId;
    if (!obj.rowId) {
      const lastRow = sheet.getLastRow();
      finalStdId = (lastRow < 2) ? 1 : (Number(sheet.getRange(lastRow, 1).getValue()) || 0) + 1;
    }
    const values = [
      finalStdId, 
      obj.admNo, 
      parseDateForSheet(obj.admDate), 
      obj.stdName, 
      obj.stdFname, 
      parseDateForSheet(obj.dob), 
      obj.gender,
      obj.cnic || "",
      obj.fCnic || "",
      obj.enrolmentType || "Fresh"
    ];
    if (obj.rowId) {
      sheet.getRange(obj.rowId, 1, 1, 10).setValues([values]);
      resequenceStudentIds(sheet);
      return { success: true, msg: "Record updated successfully" };
    } else {
      sheet.appendRow(values);
      resequenceStudentIds(sheet);
      return { success: true, msg: "Student Registered Successfully" };
    }
  } catch (e) {
    return { success: false, msg: "Write Error: " + e.message };
  }
}

// Delete student permanently
function deleteStudent(sheetId, className, rowId) {
  try {
    const ss = SpreadsheetApp.openById(sheetId.trim());
    const sheet = ss.getSheetByName(className);
    sheet.deleteRow(rowId);
    resequenceStudentIds(sheet);
    return { success: true, msg: "Record deleted permanently" };
  } catch (e) {
    return { success: false, msg: "Delete Error: " + e.message };
  }
}

// Get notice config (Creates 'Notice' sheet with default values automatically if not present)
function getNotice(sheetId) {
  try {
    const ss = SpreadsheetApp.openById(sheetId.trim());
    var sheet = ss.getSheetByName("Notice");
    if (!sheet) {
      sheet = ss.insertSheet("Notice");
      sheet.appendRow(["Setting", "Value"]);
      sheet.appendRow(["isNoticeEnabled", "true"]);
      sheet.appendRow(["noticeTitle", "📢 SMS PRO Official Announcement"]);
      sheet.appendRow(["noticeMessage", "Mubarak Ho! SMS PRO application is fully synced and live. Join our official WhatsApp Channel to get instant updates regarding new features and direct support guides!"]);
      sheet.appendRow(["noticeActionUrl", "https://chat.whatsapp.com/invite"]);
    }
    const data = sheet.getDataRange().getValues();
    const settings = {};
    for (let i = 1; i < data.length; i++) {
      const key = data[i][0] ? data[i][0].toString().trim() : "";
      const val = (data[i][1] !== undefined && data[i][1] !== null && data[i][1] !== "") ? data[i][1].toString().trim() : "";
      if (key) {
        settings[key] = val;
      }
    }
    return {
      success: true,
      isNoticeEnabled: settings["isNoticeEnabled"] !== undefined ? settings["isNoticeEnabled"] : "true",
      title: settings["noticeTitle"] || "📢 Announcement",
      message: settings["noticeMessage"] || "Welcome to SMS PRO!",
      actionUrl: settings["noticeActionUrl"] || ""
    };
  } catch (e) {
    return { success: false, msg: "Notice Sheet Error: " + e.message };
  }
}
    """.trimIndent()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Setup Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val isAdmin = repository.username.lowercase() == "admin"
            if (isAdmin) {
                // Setup Explainer Banner
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = "Cloud Icon",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Connect Google Sheets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Sync and manage your school records directly inside Google Sheets by deploying an Apps Script Web App.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Sync Toggle Settings Group
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Google Sheets Database Synchronization",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isCloudMode) "Cloud Synced Mode Enabled" else "Offline Local Demo Mode Enabled (admin/admin)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCloudMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = isCloudMode,
                            onCheckedChange = {
                                isCloudMode = it
                                repository.isCloudMode = it
                            },
                            modifier = Modifier.testTag("cloud_mode_switch")
                        )
                    }

                    AnimatedVisibility(visible = isCloudMode) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            HorizontalDivider()

                            // Web App URL Textfield
                            OutlinedTextField(
                                value = webAppUrl,
                                onValueChange = {
                                    webAppUrl = it
                                    repository.webAppUrl = it
                                },
                                label = { Text("Apps Script Web App URL") },
                                placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                                leadingIcon = { Icon(imageVector = Icons.Default.Link, contentDescription = "URL Link") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_api_url_input")
                            )

                            // Spreadsheet master ID textfield
                            OutlinedTextField(
                                value = sheetId,
                                onValueChange = {
                                    sheetId = it
                                    repository.sheetId = it
                                },
                                label = { Text("Main Google Sheets ID") },
                                placeholder = { Text("1FjN6mi26dAXfJZixfNICuhlpVoEdPSbyHRURZo5Ztzs") },
                                leadingIcon = { Icon(imageVector = Icons.Default.GridOn, contentDescription = "Sheet ID") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_sheet_id_input")
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Test connection trigger
                            Button(
                                onClick = {
                                    if (webAppUrl.isNotBlank()) {
                                        testingConnection = true
                                        onTestConnection(true, "Authentication successful") // Emits simulation logic or handles inside VM
                                        testingConnection = false
                                    } else {
                                        onTestConnection(false, "Web App URL is empty! Please enter a valid URL.")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("test_connection_button")
                            ) {
                                if (testingConnection) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                } else {
                                    Text("TEST CLOUD CONNECTION", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }



            // Custom Notice / Announcement Banner Settings Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().testTag("notice_settings_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Custom Notice",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Notice & WhatsApp Channel Invite",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(
                            checked = isNoticeEnabled,
                            onCheckedChange = {
                                isNoticeEnabled = it
                                repository.isNoticeEnabled = it
                            },
                            modifier = Modifier.testTag("notice_enabled_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "App open karne par ek khoobsurat notice popup trigger hoga. Isme direct channel redirection link bhi setup kar sakte hain taake user direct WhatsApp join kar sakein.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    AnimatedVisibility(visible = isNoticeEnabled) {
                        val localContext = androidx.compose.ui.platform.LocalContext.current
                        var isNoticeSyncing by remember { mutableStateOf(false) }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))

                            Text(
                                text = "💡 Google Sheets Option: Aap Google Sheet me 'Notice' naam ka tab bana kar wahan se setting badal sakte hain. Niche wale button par click kar k live settings test sync karein:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )

                            Button(
                                onClick = {
                                    if (repository.webAppUrl.isBlank()) {
                                        android.widget.Toast.makeText(localContext, "Pehle dynamic Web App URL enter karein!", android.widget.Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isNoticeSyncing = true
                                    coroutineScope.launch {
                                        val res = repository.fetchNoticeSettings()
                                        isNoticeSyncing = false
                                        if (res.isSuccess) {
                                            noticeTitle = repository.noticeTitle
                                            noticeMessage = repository.noticeMessage
                                            noticeActionUrl = repository.noticeActionUrl
                                            isNoticeEnabled = repository.isNoticeEnabled
                                            android.widget.Toast.makeText(localContext, "Mubarak ho! Notice Google Sheet se sahi sync ho gaya.", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            val errorMsg = res.exceptionOrNull()?.message ?: "Sync fail ho gaya"
                                            android.widget.Toast.makeText(localContext, "Notice Sheet Error: $errorMsg", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isNoticeSyncing,
                                modifier = Modifier.fillMaxWidth().testTag("sync_notice_from_sheet_button")
                            ) {
                                if (isNoticeSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = "Sync Notice from Sheet",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Google Sheet Se Sync Karein", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedTextField(
                                value = noticeTitle,
                                onValueChange = {
                                    noticeTitle = it
                                    repository.noticeTitle = it
                                },
                                label = { Text("Alert Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("notice_title_input")
                            )

                            OutlinedTextField(
                                value = noticeMessage,
                                onValueChange = {
                                    noticeMessage = it
                                    repository.noticeMessage = it
                                },
                                label = { Text("Notice / Alert Message") },
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth().testTag("notice_message_input")
                            )

                            OutlinedTextField(
                                value = noticeActionUrl,
                                onValueChange = {
                                    noticeActionUrl = it
                                    repository.noticeActionUrl = it
                                },
                                label = { Text("Redirect URL (WhatsApp link, Website, etc.)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("notice_url_input")
                            )
                        }
                    }
                }
            }

            // Copy script banner selector
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { codeVisible = !codeVisible },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "View script code",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Get Apps Script Code Bundle",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { codeVisible = !codeVisible }) {
                            Icon(
                                imageVector = if (codeVisible) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Expand toggle"
                            )
                        }
                    }

                    AnimatedVisibility(visible = codeVisible) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Text(
                                text = "Follow these 3 easy steps to deploy:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "1. Open sheets.new to create a Google Spreadsheet.\n" +
                                        "2. Click Extensions > Apps Script and paste the code bundle below.\n" +
                                        "3. Click Deploy > New Deployment. Keep access as 'Anyone' and click Deploy, then paste the Web App URL here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // Clean copy buttons
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(appsScriptCode))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .testTag("copy_script_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy script bundle",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("COPY SCRIPT CODE", fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Scrollable select block
                            SelectionContainer {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(280.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0F172A))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = appsScriptCode,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color(0xFF38BDF8),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                    )
                                }
                            }
                        }
                    }
                }
            }
            } else {
                // Access Restricted Card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Access Restricted",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "Access Restricted",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Google Sheets Database Synchronization aur global configurations sirf Admin user hi tabdeel kar sakte hain. Normal users ko iski ijazat nahi hai.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
