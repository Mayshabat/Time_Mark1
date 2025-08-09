package com.example.endproject

import android.content.Intent
import android.net.Uri


import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.endproject.adapters.ManagerAdapter
import com.example.endproject.models.EmployeeDayRecord
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.itextpdf.text.Document
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfPCell
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfWriter
import com.itextpdf.text.Phrase
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.itextpdf.text.pdf.BaseFont
import com.itextpdf.text.Element
import com.itextpdf.text.*
import com.itextpdf.text.pdf.*


class ManagerActivity : AppCompatActivity() {

    // --- רכיבי UI ראשיים במסך המנהל ---
    private lateinit var greetingText: TextView
    private lateinit var currentDateTV: TextView
    private lateinit var currentTimeTV: TextView
    private lateinit var yearSpinner: Spinner
    private lateinit var monthSpinner: Spinner
    private lateinit var logoutButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var employeeSpinner: Spinner
    private lateinit var totalHoursTV: TextView
    private lateinit var exportButton: Button

    // --- אובייקטים לעבודה שוטפת ---
    private val handler = Handler(Looper.getMainLooper()) // מציג שעון "חי" שמתעדכן כל שנייה
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault()) // פורמט לשעה
    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()) // פורמט לתאריך
    private val employeeMap = mutableMapOf<String, String>() // מיפוי שם עובד -> UID בפיירבייס
    private var lastLoadedRecords: List<EmployeeDayRecord> = emptyList() // שמירת הרשומות האחרונות שנטענו להצגה וגם לייצוא PDF

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manager)

        // --- קישור רכיבי UI מה־XML ---
        greetingText = findViewById(R.id.managerGreeting)
        currentDateTV = findViewById(R.id.currentDate)
        currentTimeTV = findViewById(R.id.currentTime)
        yearSpinner = findViewById(R.id.yearSpinner)
        monthSpinner = findViewById(R.id.monthSpinner)
        logoutButton = findViewById(R.id.logoutButton)
        recyclerView = findViewById(R.id.managerRecyclerView)
        employeeSpinner = findViewById(R.id.employeeSpinner)
        totalHoursTV = findViewById(R.id.totalHoursTextView)
        exportButton = findViewById(R.id.exportButton)
        recyclerView.layoutManager = LinearLayoutManager(this) // טבלת נוכחות כמחזור־רכיבים (RecyclerView)

        // --- ברכות ותצוגת תאריך/שעה ---
        greetingText.text = "שלום מנהל"
        currentDateTV.text = dateFormat.format(Date())
        updateLiveClock()      // מתחיל עדכון השעון כל שנייה
        initSpinners()         // מכין את בוררי שנה/חודש
        loadEmployeeList()     // טוען רשימת עובדים מה־Realtime Database
        setAutoLoadOnSelectionChange() // בכל שינוי עובד/חודש/שנה נטען מחדש את הנתונים

        // --- חיווי פעולות ---
        exportButton.setOnClickListener {
            exportRecordsToPdf() // מייצא את רשומות הנוכחות האחרונות ל־PDF ומעלה ל־Firebase Storage + רשומה ב־Realtime DB
        }

        logoutButton.setOnClickListener {
            // התנתקות מהמערכת וחזרה למסך התחברות
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    // --- מציג שעון חי המתעדכן כל שנייה באמצעות Handler על ה־Main Looper ---
    private fun updateLiveClock() {
        handler.post(object : Runnable {
            override fun run() {
                currentTimeTV.text = timeFormat.format(Date())
                handler.postDelayed(this, 1000) // ריצה חוזרת כל 1000ms
            }
        })
    }

    // --- אתחול רשימות שנה/חודש: שנים אחרונות + כל חודשי השנה ---
    private fun initSpinners() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = (currentYear - 5..currentYear).toList().reversed() // 6 שנים אחרונות, מהחדשה לישנה
        val months = (1..12).toList() // חודשים 1..12

        yearSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, years)
        monthSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, months)
        yearSpinner.setSelection(0) // כברירת מחדל: השנה הנוכחית (מופיעה ראשונה ברשימה ההפוכה)
        monthSpinner.setSelection(Calendar.getInstance().get(Calendar.MONTH)) // החודש הנוכחי (0-מבוסס, לכן +1 בהמשך)
    }

    // --- טוען רשימת עובדים מהענף "users" וממלא Spinner בשמות. במפה נשמר קשר שם->UID ---
    private fun loadEmployeeList() {
        val ref = FirebaseDatabase.getInstance().getReference("users")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val names = mutableListOf<String>()
                for (userSnap in snapshot.children) {
                    val uid = userSnap.key ?: continue
                    val name = userSnap.child("name").getValue(String::class.java) ?: "לא ידוע"
                    employeeMap[name] = uid
                    names.add(name)
                }
                val adapter = ArrayAdapter(this@ManagerActivity, android.R.layout.simple_spinner_item, names)
                employeeSpinner.adapter = adapter
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // --- בכל שינוי בחירה (עובד/חודש) נטען מחדש נתוני הנוכחות והטפסים ---
    private fun setAutoLoadOnSelectionChange() {
        val selectionListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedName = employeeSpinner.selectedItem?.toString() ?: return
                val uid = employeeMap[selectedName] ?: return
                val year = yearSpinner.selectedItem.toString().toInt()
                val month = monthSpinner.selectedItemPosition + 1 // Spinner מחזיר אינדקס 0..11, לכן מוסיפים 1
                loadEmployeeMonthlyAttendance(uid, selectedName, year, month) // נוכחות חודשית
                loadEmployeeServiceForms(uid) // טפסי שירות שהעלה אותו עובד (מתיקיית service_forms/UID)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        employeeSpinner.onItemSelectedListener = selectionListener
        monthSpinner.onItemSelectedListener = selectionListener
    }

    // --- טוען נוכחות לפי עובד/שנה/חודש מענף "attendance/{uid}" ומחשב שעות חודשיות ---
    private fun loadEmployeeMonthlyAttendance(uid: String, name: String, year: Int, month: Int) {
        val ref = FirebaseDatabase.getInstance().getReference("attendance").child(uid)
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<EmployeeDayRecord>()
                var totalMinutes = 0

                for (daySnap in snapshot.children) {
                    // המפתח הוא תאריך בפורמט d-m-y (לפי הקוד), מפרקים אותו
                    val dateParts = daySnap.key?.split("-") ?: continue
                    if (dateParts.size != 3) continue
                    val d = dateParts[0].toIntOrNull() ?: continue
                    val m = dateParts[1].toIntOrNull() ?: continue
                    val y = dateParts[2].toIntOrNull() ?: continue
                    if (m != month || y != year) continue // סינון לפי חודש/שנה שנבחרו

                    // קריאת זמני כניסה/יציאה ומיקומים (אם קיימים)
                    val checkInTime = daySnap.child("checkIn/time").getValue(String::class.java)
                    val checkOutTime = daySnap.child("checkOut/time").getValue(String::class.java)
                    val checkInLocation = daySnap.child("checkIn/location").getValue(String::class.java) ?: "-"
                    val checkOutLocation = daySnap.child("checkOut/location").getValue(String::class.java) ?: "-"

                    // מחשבים משך רק אם יש גם כניסה וגם יציאה
                    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    var durationMinutes = "-"
                    if (!checkInTime.isNullOrBlank() && !checkOutTime.isNullOrBlank()) {
                        try {
                            val checkIn = formatter.parse("$d/$m/$y $checkInTime")
                            val checkOut = formatter.parse("$d/$m/$y $checkOutTime")
                            val diff = (checkOut.time - checkIn.time) / (60 * 1000) // דלתא בדקות
                            totalMinutes += diff.toInt()
                            durationMinutes = String.format("%02d:%02d", diff / 60, diff % 60) // פורמט HH:MM
                        } catch (e: Exception) {
                            durationMinutes = "-" // אם יש בעיית פרסינג לא נשבור תצוגה
                        }
                    }

                    val comment = daySnap.child("comment").getValue(String::class.java) ?: ""
                    // בונים רשומת יום עבור המתאם (Adapter)
                    val record = EmployeeDayRecord(
                        employeeName = name,
                        date = "$d/$m/$y",
                        checkIn = checkInTime ?: "-",
                        checkOut = checkOutTime ?: "-",
                        totalHours = durationMinutes,
                        comment = comment,
                        checkInLocation = checkInLocation,
                        checkOutLocation = checkOutLocation
                    )
                    list.add(record)
                }

                lastLoadedRecords = list // נשמר לשימוש בייצוא PDF
                recyclerView.adapter = ManagerAdapter(list) // מציגים ברשימה

                // טקסט סיכום שעות חודשיות
                val totalText = if (totalMinutes > 0) {
                    val hours = totalMinutes / 60
                    val minutes = totalMinutes % 60
                    String.format("סהכ שעות החודש: %02d:%02d", hours, minutes)
                } else "לא נמצאו שעות פעילות"

                totalHoursTV.text = totalText
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // --- טוען טפסי שירות של עובד מ־Firebase Storage בתיקייה service_forms/{uid} ומציג ב־ListView ---
    // עדכהתי עכשיו
    private fun loadEmployeeServiceForms(uid: String) {
        val listView = findViewById<ListView>(R.id.uploadedFilesListView)
        val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child("service_forms/$uid")

        storageRef.listAll()
            .addOnSuccessListener { listResult ->
                if (listResult.items.isEmpty()) {
                    // אין קבצים להצגה
                    listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf("אין טפסים זמינים"))
                    listView.setOnItemClickListener(null)
                    return@addOnSuccessListener
                }

                // מציגים רק את שמות הקבצים ברשימה. בלחיצה נפתח את ה־PDF לפי URL
                val fileNames = listResult.items.map { it.name }
                val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, fileNames)
                listView.adapter = adapter

                listView.setOnItemClickListener { _, _, position, _ ->
                    val selectedFileRef = listResult.items[position]
                    selectedFileRef.downloadUrl.addOnSuccessListener { uri ->
                        openPdfFromUrl(uri) // פתיחה באמצעות אפליקציית PDF במכשיר
                    }.addOnFailureListener {
                        Toast.makeText(this, "שגיאה בטעינת הקובץ", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "שגיאה בטעינת רשימת הטפסים", Toast.LENGTH_SHORT).show()
            }
    }

//        // טענת טפסי שירות מ-Firebase


    // --- מייצא את הרשומות האחרונות ל־PDF בעברית (RTL) בעזרת iText, פותח את הקובץ, ואז מעלה ל־Storage ומעדכן ב־Realtime DB ---
    private fun exportRecordsToPdf() {
        if (lastLoadedRecords.isEmpty()) {
            Toast.makeText(this, "אין נתונים לייצוא", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // יצירת קובץ בתוך ספריית האפליקציה Documents (אין צורך בהרשאות כתיבה מיוחדות כי זו ספרייה פרטית של האפליקציה)
            val fileName = "attendance_${System.currentTimeMillis()}.pdf"
            val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
            val document = Document()
            PdfWriter.getInstance(document, FileOutputStream(file))
            document.open()

            // --- תמיכה בעברית: טעינת פונט שיודע יוניקוד + הגדרת כיוון כתיבה בהמשך (RUN_DIRECTION_RTL) ---
            val baseFont = BaseFont.createFont("assets/fonts/FreeSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED)
            val font = com.itextpdf.text.Font(baseFont, 12f)

            // --- כותרת הדו"ח ---
            val employeeName = lastLoadedRecords.first().employeeName
            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH) + 1
            val currentYear = calendar.get(Calendar.YEAR)

            val titleText = "דו\"ח נוכחות חודש $currentMonth/$currentYear - $employeeName"
            val titlePhrase = Phrase(titleText, font)

            val titleCell = PdfPCell(titlePhrase)
            titleCell.runDirection = PdfWriter.RUN_DIRECTION_RTL // כתיבה מימין לשמאל
            titleCell.border = Rectangle.NO_BORDER
            titleCell.horizontalAlignment = Element.ALIGN_RIGHT

            val titleTable = PdfPTable(1)
            titleTable.runDirection = PdfWriter.RUN_DIRECTION_RTL
            titleTable.widthPercentage = 100f
            titleTable.addCell(titleCell)

            document.add(titleTable)

            // --- טבלת הנוכחות: כותרות + שורות ימים ---
            val table = PdfPTable(5)
            table.runDirection = PdfWriter.RUN_DIRECTION_RTL
            table.widthPercentage = 100f
            table.spacingBefore = 10f
            table.spacingAfter = 10f

            val headers = listOf("תאריך", "כניסה", "יציאה", "סה\"כ", "הערה")
            for (headerText in headers) {
                val cell = PdfPCell(Phrase(headerText, font))
                cell.setPadding(6f)
                cell.horizontalAlignment = PdfPCell.ALIGN_CENTER
                table.addCell(cell)
            }

            var totalMinutes = 0
            for (record in lastLoadedRecords) {
                // הוספת הנתונים לטבלה
                table.addCell(Phrase(record.date, font))
                table.addCell(Phrase(record.checkIn, font))
                table.addCell(Phrase(record.checkOut, font))
                table.addCell(Phrase(record.totalHours, font))
                table.addCell(Phrase(record.comment ?: "-", font))

                // צבירת זמן כולל לחישוב תוצאה בסוף
                val parts = record.totalHours.split(":")
                if (parts.size == 2) {
                    val h = parts[0].toIntOrNull() ?: 0
                    val m = parts[1].toIntOrNull() ?: 0
                    totalMinutes += h * 60 + m
                }
            }

            document.add(table)

            // --- סיכום שעות בתחתית הדו"ח (RTL, מיושר לימין) ---
            val totalTimeFormatted = String.format("%02d:%02d", totalMinutes / 60, totalMinutes % 60)
            val totalText = "סה\"כ שעות החודש: $totalTimeFormatted"
            val totalPhrase = Phrase(totalText, font)

            val totalCell = PdfPCell(totalPhrase)
            totalCell.runDirection = PdfWriter.RUN_DIRECTION_RTL
            totalCell.border = Rectangle.NO_BORDER
            totalCell.horizontalAlignment = Element.ALIGN_RIGHT

            val totalTable = PdfPTable(1)
            totalTable.runDirection = PdfWriter.RUN_DIRECTION_RTL
            totalTable.widthPercentage = 100f
            totalTable.spacingBefore = 10f
            totalTable.addCell(totalCell)

            document.add(totalTable)

            document.close() // סגירת ה־PDF (חשוב לפני פתיחה/העלאה)

            // --- פתיחת הקובץ לצפייה במכשיר (אם יש אפליקציית PDF) ---
            openPdf(file)

            // --- העלאה ל־Firebase Storage בתיקייה manager_reports/{UID העובד} ---
            val selectedName = employeeSpinner.selectedItem?.toString() ?: return
            val employeeUid = employeeMap[selectedName] ?: return

            val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference
            val pdfRef = storageRef.child("manager_reports/$employeeUid/$fileName")

            pdfRef.putFile(Uri.fromFile(file))
                .addOnSuccessListener {
                    Toast.makeText(this, "קובץ המנהל נשמר ב־Firebase בהצלחה", Toast.LENGTH_SHORT).show()

                    // --- קבלת URL ושמירה ב־Realtime Database תחת "pdfForms" ---
                    // שימי לב: כאן "uploader" = UID של העובד, כדי שהקובץ יופיע אצלו גם ברשימה לפי ההיגיון של האפליקציה
                    pdfRef.downloadUrl.addOnSuccessListener { uri ->
                        val dbRef = FirebaseDatabase.getInstance().getReference("pdfForms")
                        val formId = dbRef.push().key ?: return@addOnSuccessListener
                        dbRef.child(formId).setValue(mapOf(
                            "url" to uri.toString(),
                            "uploader" to employeeUid, // ✅ זה מה שחשוב!
                            "type" to "manager_report",
                            "timestamp" to System.currentTimeMillis()
                        ))
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "שגיאה בהעלאה ל־Firebase: ${e.message}", Toast.LENGTH_SHORT).show()
                }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "שגיאה ביצירת קובץ PDF", Toast.LENGTH_SHORT).show()
        }
    }

    // --- פתיחת PDF מקובץ מקומי באמצעות FileProvider (נדרש להגדיר provider ב־Manifest ו־paths ב־xml) ---
    private fun openPdf(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NO_HISTORY
        }

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "אין אפליקציה לפתיחת PDF באמולטור", Toast.LENGTH_LONG).show()
        }
    }

    // --- פתיחת PDF לפי URL (מתאים לקבצים מה־Storage עם הרשאת גישה מתאימה) ---
    // openPdfFromUrl: פותח קובץ PDF לפי כתובת URL (Storage public URL)
    private fun openPdfFromUrl(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NO_HISTORY
        }

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "לא נמצאה אפליקציה להצגת PDF", Toast.LENGTH_SHORT).show()
        }
    }

    // --- ניקוי ה־Handler של השעון כדי למנוע דליפת זיכרון כאשר הפעילות נסגרת ---
    // onDestroy: ניקוי ה-Handler של שעון חי כדי למנוע דליפת זיכרון
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
