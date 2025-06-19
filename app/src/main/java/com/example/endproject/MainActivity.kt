package com.example.endproject

// ייבוא ספריות נחוצות לעבודה עם מיקום, Firebase, תאריך ושעון
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.location.Location
import android.location.Geocoder
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.android.gms.location.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // הגדרת כל הרכיבים שמוצגים במסך הראשי של האפליקציה
    private lateinit var userName: TextView
    private lateinit var lastEntry: TextView
    private lateinit var currentTime: TextView
    private lateinit var reportEntryButton: ImageButton
    private lateinit var reportExitButton: ImageButton
    private lateinit var personalAreaButton: Button
    private lateinit var addCommentButton: Button
    private lateinit var serviceCallButton: Button
    private lateinit var uid: String

    // רכיבי מיקום וחיבור למסד הנתונים
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var database: DatabaseReference

    // הגדרות עבור שעון ותאריך
    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_entry)

        // קבלת מזהה המשתמש המחובר
        uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // קישור רכיבי המסך לפי מזהים
        userName = findViewById(R.id.userName)
        lastEntry = findViewById(R.id.lastEntry)
        currentTime = findViewById(R.id.currentTime)
        reportEntryButton = findViewById(R.id.reportEntryButton)
        reportExitButton = findViewById(R.id.reportExitButton)
        personalAreaButton = findViewById(R.id.personalAreaButton)
        addCommentButton = findViewById(R.id.addCommentButton)
        serviceCallButton = findViewById(R.id.serviceCallButton)
        val logoutButton = findViewById<Button>(R.id.logoutButton)

        // חיבור למיקום ול־Firebase
        database = FirebaseDatabase.getInstance().reference
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        showUserName() // הצגת שם המשתמש
        updateLiveClock() // עדכון שעון חי
        findViewById<TextView>(R.id.reportDate).text = dateFormat.format(Date()) // תאריך היום

        loadTodayStatus() // בדיקת סטטוס נוכחות להיום

        // לחיצה על כניסה/יציאה
        reportEntryButton.setOnClickListener { markAttendance("checkIn") }
        reportExitButton.setOnClickListener { markAttendance("checkOut") }

        // מעבר למסך היסטוריית נוכחות
        personalAreaButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR).toString()
            val currentMonth = String.format("%02d", calendar.get(Calendar.MONTH) + 1)

            val intent = Intent(this, HistoryActivity::class.java)
            intent.putExtra("YEAR", currentYear)
            intent.putExtra("MONTH", currentMonth)
            startActivity(intent)
        }

        // מעבר למסך הוספת הערה
        addCommentButton.setOnClickListener {
            val intent = Intent(this, CommentActivity::class.java)
            startActivity(intent)
        }

        // מעבר למסך קריאת שירות
        serviceCallButton.setOnClickListener {
            val intent = Intent(this, ServiceFormActivity::class.java)
            startActivity(intent)
        }

        // לחצן יציאה – מתנתק ומחזיר למסך התחברות
        logoutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    // הצגת שם המשתמש מה־Firebase
    private fun showUserName() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(uid)

        userRef.child("name").get().addOnSuccessListener {
            val name = it.getValue(String::class.java) ?: "משתמש"
            userName.text = name
        }.addOnFailureListener {
            userName.text = "משתמש"
        }
    }

    // הפעלת שעון חי שמתעדכן כל שנייה
    private fun updateLiveClock() {
        handler.post(object : Runnable {
            override fun run() {
                currentTime.text = timeFormat.format(Date())
                handler.postDelayed(this, 1000)
            }
        })
    }

    // יצירת מיקום במסד לפי התאריך של היום
    private fun getTodayPath(): DatabaseReference {
        val today = dateFormat.format(Date())
        return database.child("attendance").child(uid).child(today)
    }

    // דיווח כניסה או יציאה, עם בקשת מיקום
    private fun markAttendance(type: String) {
        if (uid.isBlank()) {
            Toast.makeText(this, "משתמש לא מחובר", Toast.LENGTH_SHORT).show()
            return
        }

        // בדיקת הרשאות מיקום
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1001
            )
            return
        }

        // בקשת מיקום עדכני
        requestFreshLocation { location ->
            if (location == null) {
                Toast.makeText(this, "לא ניתן לקבל מיקום נוכחי", Toast.LENGTH_SHORT).show()
            } else {
                handleAttendanceWithLocation(location, type)
            }
        }
    }

    // תגובה לבקשת הרשאות
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "הרשאת מיקום אושרה", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "יש לאשר הרשאת מיקום כדי לבצע דיווח", Toast.LENGTH_SHORT).show()
        }
    }

    // טוען סטטוס כניסה/יציאה של היום
    private fun loadTodayStatus() {
        val todayPath = getTodayPath()
        todayPath.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val checkInTime = snapshot.child("checkIn/time").getValue(String::class.java)
                val checkOutTime = snapshot.child("checkOut/time").getValue(String::class.java)
                val today = dateFormat.format(Date())

                lastEntry.text = when {
                    checkOutTime != null -> "יציאה אחרונה  $today בשעה $checkOutTime"
                    checkInTime != null -> "כניסה אחרונה  $today בשעה $checkInTime"
                    else -> "אין רישום להיום"
                }

                // אם כבר נרשם – מנטרל את הלחצן
                reportEntryButton.isEnabled = checkInTime == null
                reportExitButton.isEnabled = checkOutTime == null
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "שגיאה בטעינת נתונים", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // שמירה למסד עם מיקום ושעה
    private fun handleAttendanceWithLocation(location: Location, type: String) {
        val fullDateTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val now = fullDateTimeFormat.format(Date())
        val todayPath = getTodayPath()

        todayPath.child(type).get().addOnSuccessListener {
            if (it.exists()) {
                Toast.makeText(this, "כבר נרשמה $type להיום", Toast.LENGTH_SHORT).show()
            } else {
                val geocoder = Geocoder(this, Locale.getDefault())
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                val addressText = if (!addresses.isNullOrEmpty()) {
                    addresses[0].getAddressLine(0) ?: "מיקום לא ידוע"
                } else {
                    "מיקום לא ידוע"
                }

                val attendanceMap = mapOf(
                    "time" to now,
                    "location" to addressText
                )

                todayPath.child(type).setValue(attendanceMap)
                    .addOnSuccessListener {
                        Toast.makeText(this, "$type נרשמה בשעה $now", Toast.LENGTH_SHORT).show()
                        loadTodayStatus()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "שגיאה בשמירה ל־Firebase", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    // בקשת מיקום עדכני מה-GPS
    private fun requestFreshLocation(onLocationReceived: (Location?) -> Unit) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMaxUpdates(1)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                onLocationReceived(result.lastLocation)
                fusedLocationClient.removeLocationUpdates(this)
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    onLocationReceived(null)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    // עצירה של עדכון השעון כשעוזבים את האקטיביטי
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
