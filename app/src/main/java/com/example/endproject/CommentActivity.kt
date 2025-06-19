package com.example.endproject

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * פעילות להזנת הערות יומיות על ידי עובד.
 * מאפשרת בחירה של תאריך מתוך רשימת תאריכים זמינים (שבהם דווח checkIn), והוספת הערה לטבלה.
 */
class CommentActivity : AppCompatActivity() {

    // רכיבי UI
    private lateinit var daySpinner: Spinner
    private lateinit var monthSpinner: Spinner
    private lateinit var yearSpinner: Spinner
    private lateinit var commentEditText: EditText
    private lateinit var saveCommentButton: Button
    private lateinit var backButton: Button

    // UID של המשתמש המחובר
    private val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val database = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comment)

        // קישור בין משתנים לרכיבי תצוגה
        daySpinner = findViewById(R.id.daySpinner)
        monthSpinner = findViewById(R.id.monthSpinner)
        yearSpinner = findViewById(R.id.yearSpinner)
        commentEditText = findViewById(R.id.commentEditText)
        saveCommentButton = findViewById(R.id.saveCommentButton)
        backButton = findViewById(R.id.backButton)

        loadAvailableDates() // טוען תאריכים שניתן להוסיף להם הערה

        // לחצן שמירה של ההערה
        saveCommentButton.setOnClickListener {
            val day = daySpinner.selectedItem.toString().padStart(2, '0')
            val month = monthSpinner.selectedItem.toString().padStart(2, '0')
            val year = yearSpinner.selectedItem.toString()

            val dateKey = "$day-$month-$year"  // יוצר מפתח תאריך בפורמט תואם למסד הנתונים
            val comment = commentEditText.text.toString().trim()

            if (comment.isEmpty()) {
                Toast.makeText(this, "נא להכניס תוכן להערה", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // שמירת ההערה תחת הנתיב הנכון במסד הנתונים
            database.child("attendance").child(uid).child(dateKey).child("comment").setValue(comment)
                .addOnSuccessListener {
                    Toast.makeText(this, "ההערה נשמרה בהצלחה", Toast.LENGTH_SHORT).show()
                    commentEditText.text.clear()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "שגיאה בשמירת ההערה", Toast.LENGTH_SHORT).show()
                }
        }

        // לחצן חזרה למסך הקודם
        backButton.setOnClickListener {
            finish()
        }
    }

    /**
     * טוען את רשימת התאריכים שהמשתמש דיווח עליהם (כלומר יש בהם checkIn)
     * וממלא את הספינרים (יום, חודש, שנה) בהתאם
     */
    private fun loadAvailableDates() {
        val calendar = Calendar.getInstance()
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(calendar.time)

        database.child("attendance").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val validDates = mutableListOf<String>()
                for (dateSnap in snapshot.children) {
                    if (dateSnap.hasChild("checkIn")) {
                        validDates.add(dateSnap.key ?: "")
                    }
                }

                // הוספת היום הנוכחי אם יש checkIn והוא לא קיים ברשימה
                val todaySnap = snapshot.child(today)
                if (todaySnap.hasChild("checkIn") && !validDates.contains(today)) {
                    validDates.add(today)
                }

                if (validDates.isEmpty()) {
                    Toast.makeText(this@CommentActivity, "אין תאריכים זמינים להוספת הערה", Toast.LENGTH_SHORT).show()
                    return
                }

                // ממיין את התאריכים לפי סדר יורד (מהחדש לישן)
                validDates.sortByDescending {
                    SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(it)
                }

                // מפצל את התאריכים לרשימות של ימים, חודשים ושנים ללא כפילויות
                val years = validDates.map { it.split("-")[2] }.distinct()
                val months = validDates.map { it.split("-")[1] }.distinct()
                val days = validDates.map { it.split("-")[0] }.distinct()

                // קישור הנתונים לספינרים
                yearSpinner.adapter = ArrayAdapter(this@CommentActivity, android.R.layout.simple_spinner_dropdown_item, years)
                monthSpinner.adapter = ArrayAdapter(this@CommentActivity, android.R.layout.simple_spinner_dropdown_item, months)
                daySpinner.adapter = ArrayAdapter(this@CommentActivity, android.R.layout.simple_spinner_dropdown_item, days)

                // ברירת מחדל: תאריך של היום הנוכחי אם קיים
                val todayParts = today.split("-")
                yearSpinner.setSelection(years.indexOf(todayParts[2]))
                monthSpinner.setSelection(months.indexOf(todayParts[1]))
                daySpinner.setSelection(days.indexOf(todayParts[0]))
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@CommentActivity, "שגיאה בטעינת תאריכים", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
