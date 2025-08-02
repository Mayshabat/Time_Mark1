package com.example.endproject

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.endproject.adapters.AttendanceAdapter
import com.example.endproject.models.AttendanceRecord
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import android.content.Intent
import android.net.Uri

/**
 * פעילות שמציגה היסטוריית נוכחות חודשית של עובד (כולל שעות ותגובות),
 * וכן דוחות PDF שהועלו ע״י מנהלים (אם קיימים)
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var yearSpinner: Spinner
    private lateinit var monthSpinner: Spinner
    private lateinit var recyclerView: RecyclerView
    private lateinit var totalHoursText: TextView

    private val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val dbRef: DatabaseReference =
        FirebaseDatabase.getInstance().reference.child("attendance").child(uid)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        yearSpinner = findViewById(R.id.yearSpinner)
        monthSpinner = findViewById(R.id.monthSpinner)
        recyclerView = findViewById(R.id.attendanceRecyclerView)
        totalHoursText = findViewById(R.id.totalMonthHours)

        recyclerView.layoutManager = LinearLayoutManager(this)

        initSpinners() // אתחול ספינרים של שנה וחודש

        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }

        loadManagerReports() // טוען דוחות שהועלו ע"י מנהלים
    }

    /**
     * אתחול הספינרים להצגת חודשים ושנים לבחירה
     */
    private fun initSpinners() {
        val calendar = Calendar.getInstance()
        val defaultYear = calendar.get(Calendar.YEAR)
        val defaultMonth = String.format("%02d", calendar.get(Calendar.MONTH) + 1)

        val selectedYear = intent.getIntExtra("YEAR", defaultYear).toString()
        val selectedMonth = intent.getIntExtra("MONTH", defaultMonth.toInt()).toString().padStart(2, '0')

        val years = (defaultYear - 5..defaultYear).toList().reversed()
        val months = (1..12).map { String.format("%02d", it) }

        yearSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, years)
        monthSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, months)

        yearSpinner.setSelection(years.indexOf(selectedYear.toIntOrNull() ?: defaultYear))
        monthSpinner.setSelection(months.indexOf(selectedMonth))

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadData() // טוען נתוני נוכחות לאחר בחירה
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        yearSpinner.onItemSelectedListener = listener
        monthSpinner.onItemSelectedListener = listener
    }

    /**
     * טוען את רשומות הנוכחות מה-Database לפי שנה וחודש שנבחרו,
     * כולל חישוב סך השעות החודשיות
     */
    private fun loadData() {
        val selectedYear = yearSpinner.selectedItem.toString()
        val selectedMonth = monthSpinner.selectedItem.toString()

        dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<AttendanceRecord>()
                var totalMinutes = 0

                for (daySnapshot in snapshot.children) {
                    val key = daySnapshot.key ?: continue
                    val parts = key.split("-")
                    if (parts.size != 3) continue

                    val (day, month, year) = parts

                    if (year == selectedYear && month == selectedMonth) {
                        val checkIn = daySnapshot.child("checkIn/time").getValue(String::class.java) ?: "-"
                        val checkOut = daySnapshot.child("checkOut/time").getValue(String::class.java) ?: "-"
                        val checkInLocation = daySnapshot.child("checkIn/location").getValue(String::class.java) ?: "-"
                        val checkOutLocation = daySnapshot.child("checkOut/location").getValue(String::class.java) ?: "-"
                        val comment = daySnapshot.child("comment").getValue(String::class.java) ?: ""
                        val formattedDate = "$day/$month/$year"

                        val totalHours = calculateTotalHours("$day/$month/$year $checkIn", "$day/$month/$year $checkOut")
                        totalMinutes += calculateMinutes(checkIn, checkOut)

                        list.add(
                            AttendanceRecord(
                                formattedDate,
                                checkIn,
                                checkOut,
                                checkInLocation,
                                checkOutLocation,
                                totalHours,
                                comment
                            )
                        )
                    }
                }

                if (list.isEmpty()) {
                    Toast.makeText(this@HistoryActivity, "לא נמצאו רשומות לחודש שנבחר", Toast.LENGTH_SHORT).show()
                }
                recyclerView.adapter = AttendanceAdapter(list)

                val totalText = if (totalMinutes > 0) {
                    val hours = totalMinutes / 60
                    val minutes = totalMinutes % 60
                    String.format("סה\"כ שעות החודש: %02d:%02d", hours, minutes)
                } else "לא נמצאו שעות פעילות"

                totalHoursText.text = totalText
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@HistoryActivity, "שגיאה בטעינת היסטוריה", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * מחשב את משך הזמן בין check-in ל־check-out, בפורמט של שעות ודקות
     */
    private fun calculateTotalHours(checkIn: String, checkOut: String): String {
        return try {
            val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val inDate = format.parse(checkIn) ?: return "-"
            val outDate = format.parse(checkOut) ?: return "-"
            val diffMillis = outDate.time - inDate.time
            val hours = diffMillis / (1000 * 60 * 60)
            val minutes = (diffMillis / (1000 * 60)) % 60
            String.format("%02d:%02d", hours, minutes)
        } catch (e: Exception) {
            "-"
        }
    }

    /**
     * טוען את רשימת דוחות ה־PDF של המנהלים ומציגם ב־ListView
     */
//    private fun loadManagerReports() {
//        val listView = findViewById<ListView>(R.id.managerReportsListView)
//        val dbRef = FirebaseDatabase.getInstance().getReference("pdfForms")
//
//        dbRef.orderByChild("type").equalTo("manager_report")
//            .addListenerForSingleValueEvent(object : ValueEventListener {
//                override fun onDataChange(snapshot: DataSnapshot) {
//                    val urls = mutableListOf<Pair<String, String>>()
//                    for (formSnap in snapshot.children) {
//                        val url = formSnap.child("url").getValue(String::class.java)
//                        if (!url.isNullOrEmpty()) {
//                            val name = "דו\"ח מנהל - ${formSnap.key?.takeLast(6)}"
//                            urls.add(Pair(name, url))
//                        }
//                    }
//
//                    if (urls.isEmpty()) {
//                        listView.adapter = ArrayAdapter(this@HistoryActivity , android.R.layout.simple_list_item_1, listOf("אין דוחות זמינים"))
//                        listView.setOnItemClickListener(null)
//                    } else {
//                        val names = urls.map { it.first }
//                        val adapter = ArrayAdapter(this@HistoryActivity , android.R.layout.simple_list_item_1, names)
//                        listView.adapter = adapter
//
//                        listView.setOnItemClickListener { _, _, position, _ ->
//                            val uri = Uri.parse(urls[position].second)
//                            val intent = Intent(Intent.ACTION_VIEW).apply {
//                                setDataAndType(uri, "application/pdf")
//                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NO_HISTORY
//                            }
//
//                            if (intent.resolveActivity(packageManager) != null) {
//                                startActivity(intent)
//                            } else {
//                                Toast.makeText(this@HistoryActivity , "לא נמצאה אפליקציה להצגת PDF", Toast.LENGTH_SHORT).show()
//                            }
//                        }
//                    }
//                }
//
//                override fun onCancelled(error: DatabaseError) {}
//            })
//    }
    private fun loadManagerReports() {
        val listView = findViewById<ListView>(R.id.managerReportsListView)
        val dbRef = FirebaseDatabase.getInstance().getReference("pdfForms")

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        dbRef.orderByChild("type").equalTo("manager_report")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val urls = mutableListOf<Pair<String, String>>()
                    for (formSnap in snapshot.children) {
                        val uploader = formSnap.child("uploader").getValue(String::class.java)
                        val url = formSnap.child("url").getValue(String::class.java)

                        if (uploader == currentUid && !url.isNullOrEmpty()) {
                            val name = "דו\"ח מנהל - ${formSnap.key?.takeLast(6)}"
                            urls.add(Pair(name, url))
                        }
                    }

                    if (urls.isEmpty()) {
                        listView.adapter = ArrayAdapter(this@HistoryActivity , android.R.layout.simple_list_item_1, listOf("אין דוחות זמינים"))
                        listView.setOnItemClickListener(null)
                    } else {
                        val names = urls.map { it.first }
                        val adapter = ArrayAdapter(this@HistoryActivity , android.R.layout.simple_list_item_1, names)
                        listView.adapter = adapter

                        listView.setOnItemClickListener { _, _, position, _ ->
                            val uri = Uri.parse(urls[position].second)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/pdf")
                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NO_HISTORY
                            }

                            if (intent.resolveActivity(packageManager) != null) {
                                startActivity(intent)
                            } else {
                                Toast.makeText(this@HistoryActivity , "לא נמצאה אפליקציה להצגת PDF", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }


    /**
     * מחשב את משך הזמן בדקות בין שעת התחלה לסיום בפורמט "HH:mm"
     */
    private fun calculateMinutes(start: String?, end: String?): Int {
        return try {
            val format = SimpleDateFormat("HH:mm", Locale.getDefault())
            val startTime = format.parse(start!!)
            val endTime = format.parse(end!!)
            val diff = endTime.time - startTime.time
            (diff / 60000).toInt()
        } catch (e: Exception) {
            0
        }
    }
}
