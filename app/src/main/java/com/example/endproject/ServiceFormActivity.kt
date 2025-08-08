package com.example.endproject

import android.content.Intent
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ServiceFormActivity : AppCompatActivity() {


    private lateinit var employeeNameText: EditText
    private lateinit var dateText: EditText
    private lateinit var customerNameEdit: EditText
    private lateinit var deviceTypeEdit: EditText
    private lateinit var productDescriptionEdit: EditText
    private lateinit var requestTypeGroup: RadioGroup
    private lateinit var treatmentDescriptionEdit: EditText
    private lateinit var treatmentDateEdit: EditText
    private lateinit var startTimeEdit: EditText
    private lateinit var endTimeEdit: EditText
    private lateinit var travelHoursEdit: EditText
    private lateinit var repeatTreatmentCheck: CheckBox
    private lateinit var approverNameEdit: EditText
    private lateinit var backButton: Button
    private lateinit var exportPdfButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service_form)


        employeeNameText = findViewById(R.id.employeeNameEditText)
        dateText = findViewById(R.id.autoDateEditText)
        customerNameEdit = findViewById(R.id.clientNameEditText)
        deviceTypeEdit = findViewById(R.id.deviceTypeEditText)
        productDescriptionEdit = findViewById(R.id.productDescEditText)
        requestTypeGroup = findViewById(R.id.requestTypeGroup)
        treatmentDescriptionEdit = findViewById(R.id.treatmentDescEditText)
        treatmentDateEdit = findViewById(R.id.treatmentDateEditText)
        startTimeEdit = findViewById(R.id.startTimeEditText)
        endTimeEdit = findViewById(R.id.endTimeEditText)
        travelHoursEdit = findViewById(R.id.travelHoursEditText)
        repeatTreatmentCheck = findViewById(R.id.repeatTreatmentCheckBox)
        approverNameEdit = findViewById(R.id.approverNameEditText)
        backButton = findViewById(R.id.backButton)
        exportPdfButton = findViewById(R.id.pdfButton)

        // מילוי אוטומטי של שם עובד ותאריך
        loadEmployeeName()
        loadCurrentDate()

        // חזרה אחורה
        backButton.setOnClickListener { finish() }

        // יצירת PDF בלחיצה
        exportPdfButton.setOnClickListener {
            generateStyledPdf()
        }
    }

    // שליפת שם העובד מה־Firebase (אם הוא לא התחבר דרך גוגל)
    private fun loadEmployeeName() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(uid)

        userRef.child("name").get().addOnSuccessListener {
            val name = it.getValue(String::class.java) ?: "משתמש"
            employeeNameText.setText(name)
        }.addOnFailureListener {
            employeeNameText.setText("משתמש")
        }
    }

    // הגדרת תאריך נוכחי בשדה תאריך
    private fun loadCurrentDate() {
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val currentDate = dateFormat.format(Date())
        dateText.setText(currentDate)
    }

    // יצירת PDF מעוצב עם הנתונים ושמירה לפי UID
    private fun generateStyledPdf() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "שגיאה בזיהוי המשתמש", Toast.LENGTH_SHORT).show()
            return
        }

        // קבלת סוג הקריאה מתוך ה-RadioGroup
        val selectedType = when (requestTypeGroup.checkedRadioButtonId) {
            R.id.maintenanceRadio -> "אחזקה"
            R.id.faultRadio -> "תקלה"
            R.id.serviceContractRadio -> "חוזה שירות"
            else -> "לא נבחר"
        }

        // יצירת שם קובץ לפי סוג הקריאה והזמן
        val cleanType = selectedType.replace(" ", "_")
        val fileName = "${cleanType}_form_${System.currentTimeMillis()}.pdf"
        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

        // יצירת המסמך
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()
        val pageWidth = pageInfo.pageWidth

        // ציור מסגרת
        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val margin = 30
        val rect = Rect(margin, margin, pageWidth - margin, pageInfo.pageHeight - margin)
        canvas.drawRect(rect, borderPaint)

        // ציור טקסטים
        var y = 60
        paint.textSize = 18f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("טופס קריאת שירות", (pageWidth / 2).toFloat(), y.toFloat(), paint)

        paint.textSize = 14f
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.RIGHT
        y += 40

        // פונקציה פנימית לציור שורה
        fun drawLine(label: String, value: String) {
            canvas.drawText("$label: $value", (pageWidth - 40).toFloat(), y.toFloat(), paint)
            y += 24
        }

        // כתיבת שדות הטופס ל-PDF
        drawLine("סוג קריאה", selectedType)
        drawLine("שם עובד", employeeNameText.text.toString())
        drawLine("תאריך", dateText.text.toString())
        drawLine("שם לקוח", customerNameEdit.text.toString())
        drawLine("סוג מכשיר", deviceTypeEdit.text.toString())
        drawLine("תיאור מוצר", productDescriptionEdit.text.toString())
        drawLine("מהות טיפול", treatmentDescriptionEdit.text.toString())
        drawLine("תאריך טיפול", treatmentDateEdit.text.toString())
        drawLine("שעת התחלה", startTimeEdit.text.toString())
        drawLine("שעת סיום", endTimeEdit.text.toString())
        drawLine("שעות נסיעה", travelHoursEdit.text.toString())
        drawLine("טיפול חוזר", if (repeatTreatmentCheck.isChecked) "כן" else "לא")
        drawLine("שם מאשר", approverNameEdit.text.toString())

        document.finishPage(page)
        document.writeTo(FileOutputStream(file))
        document.close()

        // שיתוף הקובץ
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "שלח טופס שירות"))

        // העלאת הקובץ ל-Firebase Storage תחת service_forms/<uid>/<שם קובץ>
        val storageRef = FirebaseStorage.getInstance().reference
        val pdfRef = storageRef.child("service_forms/$uid/$fileName")

        pdfRef.putFile(Uri.fromFile(file))
            .addOnSuccessListener {
                Toast.makeText(this, "הקובץ הועלה ל-Firebase בהצלחה", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "שגיאה בהעלאה: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
