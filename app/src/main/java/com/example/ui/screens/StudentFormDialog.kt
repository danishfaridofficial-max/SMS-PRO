package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.Student
import java.text.SimpleDateFormat
import java.util.*

fun formatCnic(input: String): String {
    val digitsOnly = input.filter { it.isDigit() }.take(13)
    val sb = StringBuilder()
    for (i in digitsOnly.indices) {
        if (i == 5) {
            sb.append("-")
        }
        if (i == 12) {
            sb.append("-")
        }
        sb.append(digitsOnly[i])
    }
    return sb.toString()
}

fun tryParseAndFormatDate(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ""

    val outFormatter = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)

    try {
        val parsed = outFormatter.parse(trimmed)
        if (parsed != null) {
            return outFormatter.format(parsed)
        }
    } catch (e: Exception) {}

    val candidateFormats = listOf(
        "d/M/yyyy",
        "dd/MM/yyyy",
        "d-M-yyyy",
        "dd-MM-yyyy",
        "yyyy-MM-dd",
        "d MMM yyyy",
        "dd MMM yyyy",
        "dd MMMM yyyy",
        "d MMMM yyyy",
        "dd-MMM-yy",
        "d-M-yy",
        "d/M/yy"
    )

    for (fmt in candidateFormats) {
        try {
            val parser = SimpleDateFormat(fmt, Locale.ENGLISH)
            parser.isLenient = false
            val parsed = parser.parse(trimmed)
            if (parsed != null) {
                val cal = Calendar.getInstance().apply { time = parsed }
                var year = cal.get(Calendar.YEAR)
                if (year < 100) {
                    year += 2000
                    cal.set(Calendar.YEAR, year)
                }
                if (year in 1900..2100) {
                    return outFormatter.format(cal.time)
                }
            }
        } catch (ignored: Exception) {}
    }

    return trimmed
}

fun isValidDateStr(input: String): Boolean {
    val formatted = tryParseAndFormatDate(input)
    if (formatted.isEmpty()) return false
    return try {
        val formatter = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
        formatter.isLenient = false
        formatter.parse(formatted) != null
    } catch (e: Exception) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentFormDialog(
    studentToEdit: Student?,
    currentClass: String,
    onDismiss: () -> Unit,
    onSave: (Student) -> Unit
) {
    val calendar = Calendar.getInstance()

    // Form inputs state
    var className by remember { mutableStateOf(studentToEdit?.className ?: currentClass) }
    var admNo by remember { mutableStateOf(studentToEdit?.admNo ?: "") }
    var admDate by remember { mutableStateOf(studentToEdit?.admDate ?: "") }
    var stdName by remember { mutableStateOf(studentToEdit?.stdName ?: "") }
    var stdFname by remember { mutableStateOf(studentToEdit?.stdFname ?: "") }
    var dob by remember { mutableStateOf(studentToEdit?.dob ?: "") }
    var gender by remember { mutableStateOf(studentToEdit?.gender ?: "Male") }
    var cnic by remember { mutableStateOf(studentToEdit?.cnic ?: "") }
    var fCnic by remember { mutableStateOf(studentToEdit?.fCnic ?: "") }
    var enrolmentType by remember { mutableStateOf(studentToEdit?.enrolmentType ?: "Fresh") }

    // Dropdown status
    var classExpanded by remember { mutableStateOf(false) }
    var genderExpanded by remember { mutableStateOf(false) }

    // Helpers to display date picker
    val dateFormatterInDb = remember { SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH) }

    var showComposeDatePicker by remember { mutableStateOf(false) }
    var compileDatePickerForDob by remember { mutableStateOf(true) } // true for dob, false for admDate

    // Set default ADM Date if empty
    LaunchedEffect(studentToEdit) {
        if (admDate.isEmpty()) {
            admDate = dateFormatterInDb.format(Date())
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Beautiful Header Banner with Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                                )
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (studentToEdit == null) Icons.Default.PersonAdd else Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = if (studentToEdit == null) "Student Registration" else "Edit Student Record",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Enter details accurately in scholastic base",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), CircleShape)
                                .size(36.dp)
                                .testTag("dismiss_dialog_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Dialog",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // SEC 1: ACADEMIC DETAILS
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ACADEMIC INFORMATION",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Custom Class Dropdown Selector mimicking user second screenshot
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "CLASS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(
                                            BorderStroke(
                                                width = if (classExpanded) 2.dp else 1.dp,
                                                color = if (classExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { classExpanded = !classExpanded }
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                        .testTag("form_class_selector")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Layers, // custom stack-like icon
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = if (className == "Nursury") "Nursery" else className,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Icon(
                                            imageVector = if (classExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (classExpanded) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            val classes = listOf("Nursury", "Prep", "Class 1", "Class 2", "Class 3", "Class 4", "Class 5")
                                            classes.forEachIndexed { index, cls ->
                                                val isSelected = className == cls
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(
                                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent
                                                        )
                                                        .clickable {
                                                            className = cls
                                                            classExpanded = false
                                                        }
                                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = if (cls == "Nursury") "Nursery" else cls,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.CheckCircle,
                                                            contentDescription = "Selected",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                if (index < classes.lastIndex) {
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // ADM No Field
                            OutlinedTextField(
                                value = admNo,
                                onValueChange = { admNo = it },
                                label = { Text("Admission Number *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Badge,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_adm_no_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                )
                            )

                            // ADM Date (Manual Input + Picker Button)
                            OutlinedTextField(
                                value = admDate,
                                onValueChange = { admDate = it },
                                label = { Text("Admission Date *") },
                                placeholder = { Text("e.g., 1/1/2026 or 01-Jan-2026") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            compileDatePickerForDob = false
                                            showComposeDatePicker = true
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = "Open Date Picker",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                isError = admDate.isNotEmpty() && !isValidDateStr(admDate),
                                supportingText = {
                                    if (admDate.isNotEmpty()) {
                                        if (!isValidDateStr(admDate)) {
                                            Text("Invalid date format. Use dd/mm/yyyy or dd-MMM-yyyy", color = MaterialTheme.colorScheme.error)
                                        } else {
                                            val converted = tryParseAndFormatDate(admDate)
                                            if (converted != admDate) {
                                                Text("Will be saved as: $converted", color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_adm_date_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }

                    // SEC 2: PERSONAL PARTICULARS
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "PERSONAL PARTICULARS",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Student Name
                            OutlinedTextField(
                                value = stdName,
                                onValueChange = { stdName = it },
                                label = { Text("Student Name *") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_std_name_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                )
                            )

                            // Father Name
                            OutlinedTextField(
                                value = stdFname,
                                onValueChange = { stdFname = it },
                                label = { Text("Father/Guardian Name *") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.People,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_father_name_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                )
                            )

                            // Date of Birth (Manual Input + Picker Button)
                            OutlinedTextField(
                                value = dob,
                                onValueChange = { dob = it },
                                label = { Text("Date of Birth *") },
                                placeholder = { Text("e.g., 1/1/2006 or 01-Jan-2006") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Cake,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            compileDatePickerForDob = true
                                            showComposeDatePicker = true
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = "Open Date Picker",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                isError = dob.isNotEmpty() && !isValidDateStr(dob),
                                supportingText = {
                                    if (dob.isNotEmpty()) {
                                        if (!isValidDateStr(dob)) {
                                            Text("Invalid date format. Use dd/mm/yyyy or dd-MMM-yyyy", color = MaterialTheme.colorScheme.error)
                                        } else {
                                            val converted = tryParseAndFormatDate(dob)
                                            if (converted != dob) {
                                                Text("Will be saved as: $converted", color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_dob_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                )
                            )

                            // Gender Dropdown
                            ExposedDropdownMenuBox(
                                expanded = genderExpanded,
                                onExpandedChange = { genderExpanded = !genderExpanded },
                            ) {
                                OutlinedTextField(
                                    value = gender,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Gender *") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Face,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                        .testTag("form_gender_selector"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = genderExpanded,
                                    onDismissRequest = { genderExpanded = false }
                                ) {
                                    listOf("Male", "Female").forEach { gen ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = if (gen == "Male") Icons.Default.Male else Icons.Default.Female,
                                                        contentDescription = null,
                                                        tint = if (gen == "Male") Color(0xFF1E88E5) else Color(0xFFEC407A),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(gen, fontWeight = FontWeight.SemiBold)
                                                }
                                            },
                                            onClick = {
                                                gender = gen
                                                genderExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // CNIC (Optional)
                            OutlinedTextField(
                                value = cnic,
                                onValueChange = { cnic = formatCnic(it) },
                                label = { Text("CNIC (Optional)") },
                                placeholder = { Text("12345-1234567-1") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CreditCard,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                isError = cnic.isNotEmpty() && cnic.length != 15,
                                supportingText = {
                                    if (cnic.isNotEmpty() && cnic.length != 15) {
                                        Text("Incomplete CNIC. Expected format: 12345-1234567-1", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_cnic_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                )
                            )

                            // F_CNIC (Optional)
                            OutlinedTextField(
                                value = fCnic,
                                onValueChange = { fCnic = formatCnic(it) },
                                label = { Text("Father CNIC (Optional)") },
                                placeholder = { Text("12345-1234567-1") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CreditCard,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                isError = fCnic.isNotEmpty() && fCnic.length != 15,
                                supportingText = {
                                    if (fCnic.isNotEmpty() && fCnic.length != 15) {
                                        Text("Incomplete Father CNIC. Expected format: 12345-1234567-1", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_f_cnic_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                )
                            )

                            // Enrolment Type Dropdown
                            var enrolmentExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = enrolmentExpanded,
                                onExpandedChange = { enrolmentExpanded = !enrolmentExpanded },
                            ) {
                                OutlinedTextField(
                                    value = enrolmentType,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Enrolment Type *") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Assignment,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = enrolmentExpanded) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor()
                                        .testTag("form_enrolment_selector"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = enrolmentExpanded,
                                    onDismissRequest = { enrolmentExpanded = false }
                                ) {
                                    listOf("Fresh", "Private", "Dropout", "Public").forEach { type ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = when(type) {
                                                            "Fresh" -> Icons.Default.Star
                                                            "Private" -> Icons.Default.Lock
                                                            "Dropout" -> Icons.Default.Warning
                                                            else -> Icons.Default.Public
                                                        },
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(type, fontWeight = FontWeight.SemiBold)
                                                }
                                            },
                                            onClick = {
                                                enrolmentType = type
                                                enrolmentExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Action Buttons at Bottom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("cancel_form_button")
                    ) {
                        Text("Dismiss", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    val isCnicValid = cnic.isBlank() || cnic.length == 15
                    val isFCnicValid = fCnic.isBlank() || fCnic.length == 15
                    val isAdmDateValid = admDate.isNotBlank() && isValidDateStr(admDate)
                    val isDobValid = dob.isNotBlank() && isValidDateStr(dob)

                    val isFormValid = admNo.isNotBlank() && 
                                      isAdmDateValid && 
                                      stdName.isNotBlank() && 
                                      stdFname.isNotBlank() && 
                                      isDobValid && 
                                      gender.isNotBlank() &&
                                      isCnicValid &&
                                      isFCnicValid

                    Button(
                        onClick = {
                            if (isFormValid) {
                                onSave(
                                    Student(
                                        localId = studentToEdit?.localId ?: 0L,
                                        rowId = studentToEdit?.rowId,
                                        stdId = studentToEdit?.stdId,
                                        admNo = admNo,
                                        admDate = tryParseAndFormatDate(admDate),
                                        stdName = stdName,
                                        stdFname = stdFname,
                                        dob = tryParseAndFormatDate(dob),
                                        gender = gender,
                                        className = className,
                                        cnic = if (cnic.isBlank()) null else cnic,
                                        fCnic = if (fCnic.isBlank()) null else fCnic,
                                        enrolmentType = enrolmentType
                                    )
                                )
                            }
                        },
                        enabled = isFormValid,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("save_form_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SAVE RECORD", fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                    }
                }
            }
        }
    }

    if (showComposeDatePicker) {
        val initialDateStr = if (compileDatePickerForDob) dob else admDate
        val parsedMillis = remember(initialDateStr) {
            if (initialDateStr.isNotEmpty()) {
                try {
                    val d = dateFormatterInDb.parse(initialDateStr)
                    d?.time
                } catch (ignored: Exception) {
                    try {
                        val alternateParser = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                        val d = alternateParser.parse(initialDateStr)
                        d?.time
                    } catch (e: Exception) {
                        null
                    }
                }
            } else {
                null
            }
        }

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = parsedMillis ?: System.currentTimeMillis()
        )

        DatePickerDialog(
            onDismissRequest = { showComposeDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val formatter = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }
                            val formattedDate = formatter.format(Date(selectedMillis))
                            if (compileDatePickerForDob) {
                                dob = formattedDate
                            } else {
                                admDate = formattedDate
                            }
                        }
                        showComposeDatePicker = false
                    },
                    modifier = Modifier.testTag("date_picker_confirm_button")
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showComposeDatePicker = false },
                    modifier = Modifier.testTag("date_picker_dismiss_button")
                ) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("m3_date_picker_dialog")
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
