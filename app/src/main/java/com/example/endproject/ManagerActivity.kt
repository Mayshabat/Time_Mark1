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

    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    private val employeeMap = mutableMapOf<String, String>()
    private var lastLoadedRecords: List<EmployeeDayRecord> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manager)

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
        recyclerView.layoutManager = LinearLayoutManager(this)

        greetingText.text = "שלום מנהל"
        currentDateTV.text = dateFormat.format(Date())
        updateLiveClock()
        initSpinners()
        loadEmployeeList()
        setAutoLoadOnSelectionChange() // 💡 טעינה אוטומטית
//        loadUploadedServiceForms()
//        loadEmployeeServiceForms(uid)

        exportButton.setOnClickListener {
            exportRecordsToPdf()
        }

        logoutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun updateLiveClock() {
        handler.post(object : Runnable {
            override fun run() {
                currentTimeTV.text = timeFormat.format(Date())
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun initSpinners() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = (currentYear - 5..currentYear).toList().reversed()
        val months = (1..12).toList()

        yearSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, years)
        monthSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, months)
        yearSpinner.setSelection(0)
        monthSpinner.setSelection(Calendar.getInstance().get(Calendar.MONTH))
    }

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

    private fun setAutoLoadOnSelectionChange() {
        val selectionListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedName = employeeSpinner.selectedItem?.toString() ?: return
                val uid = employeeMap[selectedName] ?: return
                val year = yearSpinner.selectedItem.toString().toInt()
                val month = monthSpinner.selectedItemPosition + 1
                loadEmployeeMonthlyAttendance(uid, selectedName, year, month)
                loadEmployeeServiceForms(uid)

            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        employeeSpinner.onItemSelectedListener = selectionListener
        monthSpinner.onItemSelectedListener = selectionListener
    }


    private fun loadEmployeeMonthlyAttendance(uid: String, name: String, year: Int, month: Int) {
        val ref = FirebaseDatabase.getInstance().getReference("attendance").child(uid)
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<EmployeeDayRecord>()
                var totalMinutes = 0

                for (daySnap in snapshot.children) {
                    val dateParts = daySnap.key?.split("-") ?: continue
                    if (dateParts.size != 3) continue
                    val d = dateParts[0].toIntOrNull() ?: continue
                    val m = dateParts[1].toIntOrNull() ?: continue
                    val y = dateParts[2].toIntOrNull() ?: continue
                    if (m != month || y != year) continue

                    val checkInTime = daySnap.child("checkIn/time").getValue(String::class.java)
                    val checkOutTime = daySnap.child("checkOut/time").getValue(String::class.java)
                    val checkInLocation = daySnap.child("checkIn/location").getValue(String::class.java) ?: "-"
                    val checkOutLocation = daySnap.child("checkOut/location").getValue(String::class.java) ?: "-"

                    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    var durationMinutes = "-"
                    if (!checkInTime.isNullOrBlank() && !checkOutTime.isNullOrBlank()) {
                        try {
                            val checkIn = formatter.parse("$d/$m/$y $checkInTime")
                            val checkOut = formatter.parse("$d/$m/$y $checkOutTime")
                            val diff = (checkOut.time - checkIn.time) / (60 * 1000)
                            totalMinutes += diff.toInt()
                            durationMinutes = String.format("%02d:%02d", diff / 60, diff % 60)
                        } catch (e: Exception) {
                            durationMinutes = "-"
                        }
                    }

                    val comment = daySnap.child("comment").getValue(String::class.java) ?: ""
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

                lastLoadedRecords = list
                recyclerView.adapter = ManagerAdapter(list)

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
    //עדכהתי עכשיו
    private fun loadEmployeeServiceForms(uid: String) {
        val listView = findViewById<ListView>(R.id.uploadedFilesListView)
        val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child("service_forms/$uid")

        storageRef.listAll()
            .addOnSuccessListener { listResult ->
                if (listResult.items.isEmpty()) {
                    listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf("אין טפסים זמינים"))
                    listView.setOnItemClickListener(null)
                    return@addOnSuccessListener
                }

                val fileNames = listResult.items.map { it.name }
                val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, fileNames)
                listView.adapter = adapter

                listView.setOnItemClickListener { _, _, position, _ ->
                    val selectedFileRef = listResult.items[position]
                    selectedFileRef.downloadUrl.addOnSuccessListener { uri ->
                        openPdfFromUrl(uri)
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
//    private fun loadUploadedServiceForms() {
//        val listView = findViewById<ListView>(R.id.uploadedFilesListView)
//        val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child("service_forms")
//
//        storageRef.listAll()
//            .addOnSuccessListener { listResult ->
//                val fileNames = listResult.items.map { it.name }
//                val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, fileNames)
//                listView.adapter = adapter
//
//                listView.setOnItemClickListener { _, _, position, _ ->
//                    val selectedFileRef = listResult.items[position]
//                    selectedFileRef.downloadUrl.addOnSuccessListener { uri ->
//                        openPdfFromUrl(uri)
//                    }.addOnFailureListener {
//                        Toast.makeText(this, "שגיאה בטעינת הקובץ", Toast.LENGTH_SHORT).show()
//                    }
//                }
//            }
//            .addOnFailureListener {
//                Toast.makeText(this, "שגיאה בטעינת רשימת הטפסים", Toast.LENGTH_SHORT).show()
//            }
//    }


    private fun exportRecordsToPdf() {
        if (lastLoadedRecords.isEmpty()) {
            Toast.makeText(this, "אין נתונים לייצוא", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val fileName = "attendance_${System.currentTimeMillis()}.pdf"
            val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
            val document = Document()
            PdfWriter.getInstance(document, FileOutputStream(file))
            document.open()

            // טעינת פונטים עבריים
            val baseFont = BaseFont.createFont("assets/fonts/FreeSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED)
            val font = com.itextpdf.text.Font(baseFont, 12f)

            // כותרת
            val employeeName = lastLoadedRecords.first().employeeName
            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH) + 1
            val currentYear = calendar.get(Calendar.YEAR)

            val titleText = "דו\"ח נוכחות חודש $currentMonth/$currentYear - $employeeName"
            val titlePhrase = Phrase(titleText, font)

            val titleCell = PdfPCell(titlePhrase)
            titleCell.runDirection = PdfWriter.RUN_DIRECTION_RTL
            titleCell.border = Rectangle.NO_BORDER
            titleCell.horizontalAlignment = Element.ALIGN_RIGHT

            val titleTable = PdfPTable(1)
            titleTable.runDirection = PdfWriter.RUN_DIRECTION_RTL
            titleTable.widthPercentage = 100f
            titleTable.addCell(titleCell)

            document.add(titleTable)


            // טבלת נוכחות
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
                table.addCell(Phrase(record.date, font))
                table.addCell(Phrase(record.checkIn, font))
                table.addCell(Phrase(record.checkOut, font))
                table.addCell(Phrase(record.totalHours, font))
                table.addCell(Phrase(record.comment ?: "-", font))

                val parts = record.totalHours.split(":")
                if (parts.size == 2) {
                    val h = parts[0].toIntOrNull() ?: 0
                    val m = parts[1].toIntOrNull() ?: 0
                    totalMinutes += h * 60 + m
                }
            }

            document.add(table)
            //לסדר את הכיתוב משמאל לימין בPDF
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

            document.close()
            openPdf(file)

// 🔄 שמירת קובץ PDF של המנהל ל־Firebase Storage
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference
            val pdfRef = storageRef.child("manager_reports/$uid/$fileName")

            pdfRef.putFile(Uri.fromFile(file))
                .addOnSuccessListener {
                    Toast.makeText(this, "קובץ המנהל נשמר ב־Firebase בהצלחה", Toast.LENGTH_SHORT).show()

                    // שמירה ל־Realtime Database (אם רוצים להציג ברשימה)
                    pdfRef.downloadUrl.addOnSuccessListener { uri ->
                        val dbRef = FirebaseDatabase.getInstance().getReference("pdfForms")
                        val formId = dbRef.push().key ?: return@addOnSuccessListener
                        dbRef.child(formId).setValue(mapOf(
                            "url" to uri.toString(),
                            "uploader" to uid,
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
